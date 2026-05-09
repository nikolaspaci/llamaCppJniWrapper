#include "JNIMethods/PredictJni.hpp"
#include "jni.h"
#include "common.h"
#include "sampling.h"
#include "llama-cpp.h"
#include "llama.h"
#include "chat.h"
#include "session/LlamaSession.hpp"
#include <iostream>
#include <string>
#include <vector>
#include <sstream>
#include <cstring>

// State machine for parsing <think> tags in streaming output
enum class ThinkParseState {
    INSIDE_THINK,   // Currently inside thinking content
    OUTSIDE_THINK,  // Normal response content
    DETECTING_TAG   // Buffering to detect </think> close tag
};

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr,
    jstring prompt_j,
    jobject modelParameters,
    jboolean enableThinking,
    jobject callback_obj) {

    // --- SETUP DU CALLBACK ---
    jclass callback_class = env->GetObjectClass(callback_obj);
    if (callback_class == nullptr) { return; }

    jmethodID on_token_method   = env->GetMethodID(callback_class, "onToken",   "(Ljava/lang/String;)V");
    jmethodID on_thinking_token_method = env->GetMethodID(callback_class, "onThinkingToken", "(Ljava/lang/String;)V");
    jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "(DJ)V");
    jmethodID on_error_method   = env->GetMethodID(callback_class, "onError",   "(Ljava/lang/String;)V");

    if (on_token_method == nullptr || on_complete_method == nullptr || on_error_method == nullptr) {
        return;
    }
    // onThinkingToken is optional - if not found, clear exception and continue
    if (on_thinking_token_method == nullptr) {
        env->ExceptionClear();
    }

    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        jstring error_msg = env->NewStringUTF("Erreur: La session Llama est invalide.");
        env->CallVoidMethod(callback_obj, on_error_method, error_msg);
        return;
    }

    // Prevent concurrent predict() calls on the same session
    std::lock_guard<std::mutex> lock(session->predictMutex);
    session->cancelRequested.store(false);
    const int saved_n_past = session->n_past;

    // Find the ModelParameter class and its fields
    jclass modelParamsClass = env->FindClass("com/nikolaspaci/app/llamallmlocal/data/database/ModelParameter");
    jfieldID temperatureField = env->GetFieldID(modelParamsClass, "temperature", "F");
    jfieldID topKField = env->GetFieldID(modelParamsClass, "topK", "I");
    jfieldID topPField = env->GetFieldID(modelParamsClass, "topP", "F");
    jfieldID minPField = env->GetFieldID(modelParamsClass, "minP", "F");
    jfieldID maxTokensField = env->GetFieldID(modelParamsClass, "maxTokens", "I");
    jfieldID repeatPenaltyField = env->GetFieldID(modelParamsClass, "repeatPenalty", "F");

    jfieldID systemPromptField = env->GetFieldID(modelParamsClass, "systemPrompt", "Ljava/lang/String;");

    // Get the values from the modelParameters object
    jfloat temperature = env->GetFloatField(modelParameters, temperatureField);
    jint topK = env->GetIntField(modelParameters, topKField);
    jfloat topP = env->GetFloatField(modelParameters, topPField);
    jfloat minP = env->GetFloatField(modelParameters, minPField);
    jint maxTokens = env->GetIntField(modelParameters, maxTokensField);
    jfloat repeatPenalty = env->GetFloatField(modelParameters, repeatPenaltyField);

    // Extract system prompt and inject if messages are empty (fallback if restoreHistory wasn't called)
    jstring systemPrompt_j = (jstring)env->GetObjectField(modelParameters, systemPromptField);
    const char* sp_c = env->GetStringUTFChars(systemPrompt_j, nullptr);
    std::string systemPromptStr(sp_c);
    env->ReleaseStringUTFChars(systemPrompt_j, sp_c);
    if (session->chatMessages.empty() && !systemPromptStr.empty()) {
        common_chat_msg sys_msg;
        sys_msg.role = "system";
        sys_msg.content = systemPromptStr;
        session->chatMessages.push_back(std::move(sys_msg));
    }

    // Update the session parameters
    session->sparams.temp = temperature;
    session->sparams.top_k = topK;
    session->sparams.top_p = topP;
    session->sparams.min_p = minP;
    session->sparams.penalty_repeat = repeatPenalty;

    const llama_model* model = session->model.get();
    llama_context* context = session->context.get();
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // 1. Add user message to history
    const char *prompt_c = env->GetStringUTFChars(prompt_j, nullptr);
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(prompt_j, prompt_c);

    common_chat_msg user_msg;
    user_msg.role = "user";
    user_msg.content = prompt_str;
    session->chatMessages.push_back(std::move(user_msg));

    // 2. Apply chat template using common_chat_templates_apply
    std::string formatted_prompt;
    bool thinking_forced_open = false;
    const bool wantThinking = (enableThinking == JNI_TRUE) && session->thinkingSupported;

    auto applyWithFallback = [&]() -> bool {
        if (session->chatTemplates) {
            // Use Jinja for thinking-capable models (both to enable and disable thinking)
            if (session->thinkingSupported) {
                try {
                    common_chat_templates_inputs inputs;
                    inputs.messages = session->chatMessages;
                    inputs.add_generation_prompt = true;
                    inputs.use_jinja = true;
                    inputs.enable_thinking = wantThinking;

                    auto chat_params = common_chat_templates_apply(session->chatTemplates.get(), inputs);
                    formatted_prompt = chat_params.prompt;
                    thinking_forced_open = wantThinking ? chat_params.thinking_forced_open : false;
                    if (!formatted_prompt.empty()) return true;
                } catch (...) {
                    // Jinja failed, fall through to non-Jinja path
                }
            }

            // Non-Jinja path (for non-thinking models, or Jinja fallback)
            try {
                common_chat_templates_inputs inputs;
                inputs.messages = session->chatMessages;
                inputs.add_generation_prompt = true;
                inputs.use_jinja = false;
                inputs.enable_thinking = false;

                auto chat_params = common_chat_templates_apply(session->chatTemplates.get(), inputs);
                formatted_prompt = chat_params.prompt;
                thinking_forced_open = false;
                if (!formatted_prompt.empty()) return true;
            } catch (...) {
                // Fall through to legacy C API
            }
        }

        // Legacy fallback: use the old C API
        const char* key = "tokenizer.chat_template";
        std::vector<char> template_buffer(8192);
        llama_model_meta_val_str(model, key, template_buffer.data(), 8192);

        std::vector<llama_chat_message> legacy_msgs;
        for (const auto& msg : session->chatMessages) {
            legacy_msgs.push_back({msg.role.c_str(), msg.content.c_str()});
        }

        std::vector<char> fmtBuf(4096);
        int prompt_size = llama_chat_apply_template(template_buffer.data(), legacy_msgs.data(), legacy_msgs.size(), true,
                                                    fmtBuf.data(), fmtBuf.size());
        if (prompt_size <= 0) return false;
        fmtBuf.resize(prompt_size);
        llama_chat_apply_template(template_buffer.data(), legacy_msgs.data(), legacy_msgs.size(), true,
                                  fmtBuf.data(), fmtBuf.size());
        formatted_prompt.assign(fmtBuf.begin(), fmtBuf.begin() + prompt_size);
        thinking_forced_open = false;
        return true;
    };

    if (!applyWithFallback()) {
        llama_memory_seq_rm(llama_get_memory(context), 0, saved_n_past, -1);
        session->n_past = saved_n_past;
        session->chatMessages.pop_back(); // Remove the user message we just added
        env->CallVoidMethod(callback_obj, on_error_method, env->NewStringUTF("Erreur: impossible d'appliquer le template de chat."));
        return;
    }

    // 3. Tokenize the full formatted prompt
    std::vector<llama_token> tokens = common_tokenize(vocab, formatted_prompt, true, true);

    if (tokens.empty()) {
        llama_memory_seq_rm(llama_get_memory(context), 0, saved_n_past, -1);
        session->n_past = saved_n_past;
        session->chatMessages.pop_back();
        env->CallVoidMethod(callback_obj, on_error_method, env->NewStringUTF("Erreur lors de la tokenisation."));
        return;
    }

    // config of batch processing
    const int n_ctx = llama_n_ctx(context);
    llama_batch batch = llama_batch_init(n_ctx, 0, 1);

    // Processing the prompt
    int processed_tokens = session->n_past;
    const int n_tokens = tokens.size();
    while (processed_tokens < n_tokens) {
        const int chunk_size = std::min(n_ctx, n_tokens - processed_tokens);
        common_batch_clear(batch);
        for (int i = 0; i < chunk_size; ++i) {
            const int token_idx = processed_tokens + i;
            const bool need_logits = (token_idx == n_tokens - 1);
            common_batch_add(batch, tokens[token_idx], token_idx, {0}, need_logits);
        }
        if (session->cancelRequested.load()) {
            llama_batch_free(batch);
            llama_memory_seq_rm(llama_get_memory(context), 0, saved_n_past, -1);
            session->n_past = saved_n_past;
            return;
        }
        const int decodeId = llama_decode(context, batch);
        if (decodeId != 0) {
            llama_batch_free(batch);
            env->CallVoidMethod(callback_obj, on_error_method, env->NewStringUTF("Erreur lors de l'évaluation du prompt."));
            return;
        }
        processed_tokens += chunk_size;
    }

    // Configure the generation
    std::stringstream response_ss;
    std::stringstream thinking_ss;
    const int max_new_tokens = maxTokens;
    int n_cur = n_tokens;

    common_sampler *smpl = common_sampler_init(model, session->sparams);
    if (!smpl) {
        llama_batch_free(batch);
        env->CallVoidMethod(callback_obj, on_error_method, env->NewStringUTF("Erreur d'initialisation du sampler."));
        return;
    }

    auto start_time = std::chrono::high_resolution_clock::now();
    int tokens_generated = 0;

    // Thinking state machine
    bool useThinkParsing = (enableThinking == JNI_TRUE) && session->thinkingSupported && thinking_forced_open;
    ThinkParseState thinkState = useThinkParsing ? ThinkParseState::INSIDE_THINK : ThinkParseState::OUTSIDE_THINK;
    std::string tagBuffer;
    static const std::string THINK_CLOSE_TAG = "</think>";

    // Generation loop
    for (int i = 0; i < max_new_tokens && n_cur < n_ctx; ++i) {
        if (session->cancelRequested.load()) {
            break;
        }

        const llama_token new_token_id = common_sampler_sample(smpl, context, batch.n_tokens - 1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        common_sampler_accept(smpl, new_token_id, true);
        std::string piece = common_token_to_piece(context, new_token_id, true);

        if (!piece.empty()) {
            if (useThinkParsing) {
                // Process each character through the state machine
                for (size_t ci = 0; ci < piece.size(); ++ci) {
                    char ch = piece[ci];

                    switch (thinkState) {
                        case ThinkParseState::INSIDE_THINK:
                            if (ch == '<') {
                                // Might be start of </think> tag
                                tagBuffer.clear();
                                tagBuffer += ch;
                                thinkState = ThinkParseState::DETECTING_TAG;
                            } else {
                                // Regular thinking content
                                thinking_ss << ch;
                                if (on_thinking_token_method) {
                                    std::string s(1, ch);
                                    jstring token_j = env->NewStringUTF(s.c_str());
                                    env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                    env->DeleteLocalRef(token_j);
                                }
                            }
                            break;

                        case ThinkParseState::DETECTING_TAG:
                            tagBuffer += ch;
                            if (tagBuffer.size() <= THINK_CLOSE_TAG.size()) {
                                // Check if still matching </think>
                                if (THINK_CLOSE_TAG.compare(0, tagBuffer.size(), tagBuffer) == 0) {
                                    if (tagBuffer.size() == THINK_CLOSE_TAG.size()) {
                                        // Complete </think> tag found - switch to response mode
                                        thinkState = ThinkParseState::OUTSIDE_THINK;
                                        tagBuffer.clear();
                                    }
                                    // else keep buffering
                                } else {
                                    // Not a </think> tag - flush buffer as thinking content
                                    thinking_ss << tagBuffer;
                                    if (on_thinking_token_method) {
                                        jstring token_j = env->NewStringUTF(tagBuffer.c_str());
                                        env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                        env->DeleteLocalRef(token_j);
                                    }
                                    tagBuffer.clear();
                                    thinkState = ThinkParseState::INSIDE_THINK;
                                }
                            } else {
                                // Buffer overflow - flush as thinking content
                                thinking_ss << tagBuffer;
                                if (on_thinking_token_method) {
                                    jstring token_j = env->NewStringUTF(tagBuffer.c_str());
                                    env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                    env->DeleteLocalRef(token_j);
                                }
                                tagBuffer.clear();
                                thinkState = ThinkParseState::INSIDE_THINK;
                            }
                            break;

                        case ThinkParseState::OUTSIDE_THINK:
                            response_ss << ch;
                            // Batch send is handled below
                            break;
                    }
                }

                // Send accumulated response content as a single token callback
                if (thinkState == ThinkParseState::OUTSIDE_THINK) {
                    std::string responseChunk;
                    // Extract only the new chars that went to response_ss in this piece
                    // We do this by checking what was added in this iteration
                    std::string fullResponse = response_ss.str();
                    static thread_local size_t lastResponseLen = 0;
                    if (i == 0) lastResponseLen = 0; // Reset at start of generation
                    if (fullResponse.size() > lastResponseLen) {
                        responseChunk = fullResponse.substr(lastResponseLen);
                        lastResponseLen = fullResponse.size();
                        if (!responseChunk.empty()) {
                            jstring token_j = env->NewStringUTF(responseChunk.c_str());
                            env->CallVoidMethod(callback_obj, on_token_method, token_j);
                            env->DeleteLocalRef(token_j);
                        }
                    }
                }
            } else {
                // No thinking parsing - send directly as response
                jstring token_j = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(callback_obj, on_token_method, token_j);
                env->DeleteLocalRef(token_j);
                response_ss << piece;
            }
        }

        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);

        if (llama_decode(context, batch) != 0) {
            break;
        }
        n_cur++;
        tokens_generated++;
    }

    // Flush any remaining tag buffer as thinking content
    if (!tagBuffer.empty() && on_thinking_token_method) {
        thinking_ss << tagBuffer;
        jstring token_j = env->NewStringUTF(tagBuffer.c_str());
        env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
        env->DeleteLocalRef(token_j);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
    const long duration_count_seconds = duration.count() / 1000;
    const double tokens_per_second = (double)tokens_generated / (duration.count() / 1000.0);

    common_sampler_free(smpl);
    llama_batch_free(batch);

    std::string response_str = response_ss.str();
    std::string thinking_str = thinking_ss.str();

    // If cancelled during generation, keep the partial state in context
    if (session->cancelRequested.load()) {
        session->n_past = n_cur;
        if (!response_str.empty() || !thinking_str.empty()) {
            common_chat_msg assistant_msg;
            assistant_msg.role = "assistant";
            assistant_msg.content = response_str;
            assistant_msg.reasoning_content = thinking_str;
            session->chatMessages.push_back(std::move(assistant_msg));
        }
        return;
    }

    session->n_past = n_cur;

    // 6. Add assistant's response to history
    common_chat_msg assistant_msg;
    assistant_msg.role = "assistant";
    assistant_msg.content = response_str;
    assistant_msg.reasoning_content = thinking_str;
    session->chatMessages.push_back(std::move(assistant_msg));

    env->CallVoidMethod(
        callback_obj,
        on_complete_method,
        tokens_per_second,
        duration_count_seconds
    );
}

// Predict with media (image) support
extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predictWithMedia(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr,
    jstring prompt_j,
    jbyteArray imageData,
    jobject modelParameters,
    jboolean enableThinking,
    jobject callback_obj) {

    // --- SETUP DU CALLBACK ---
    jclass callback_class = env->GetObjectClass(callback_obj);
    if (callback_class == nullptr) { return; }

    jmethodID on_token_method   = env->GetMethodID(callback_class, "onToken",   "(Ljava/lang/String;)V");
    jmethodID on_thinking_token_method = env->GetMethodID(callback_class, "onThinkingToken", "(Ljava/lang/String;)V");
    jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "(DJ)V");
    jmethodID on_error_method   = env->GetMethodID(callback_class, "onError",   "(Ljava/lang/String;)V");

    if (on_token_method == nullptr || on_complete_method == nullptr || on_error_method == nullptr) {
        return;
    }
    if (on_thinking_token_method == nullptr) {
        env->ExceptionClear();
    }

    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        jstring error_msg = env->NewStringUTF("Erreur: La session Llama est invalide.");
        env->CallVoidMethod(callback_obj, on_error_method, error_msg);
        return;
    }

    if (!session->mtmdCtx) {
        jstring error_msg = env->NewStringUTF("Erreur: Le contexte multimodal n'est pas initialise.");
        env->CallVoidMethod(callback_obj, on_error_method, error_msg);
        return;
    }

    std::lock_guard<std::mutex> lock(session->predictMutex);
    session->cancelRequested.store(false);
    const int saved_n_past = session->n_past;

    // Extract model parameters
    jclass modelParamsClass = env->FindClass("com/nikolaspaci/app/llamallmlocal/data/database/ModelParameter");
    jfieldID temperatureField = env->GetFieldID(modelParamsClass, "temperature", "F");
    jfieldID topKField = env->GetFieldID(modelParamsClass, "topK", "I");
    jfieldID topPField = env->GetFieldID(modelParamsClass, "topP", "F");
    jfieldID minPField = env->GetFieldID(modelParamsClass, "minP", "F");
    jfieldID maxTokensField = env->GetFieldID(modelParamsClass, "maxTokens", "I");
    jfieldID repeatPenaltyField = env->GetFieldID(modelParamsClass, "repeatPenalty", "F");
    jfieldID systemPromptField = env->GetFieldID(modelParamsClass, "systemPrompt", "Ljava/lang/String;");

    jfloat temperature = env->GetFloatField(modelParameters, temperatureField);
    jint topK = env->GetIntField(modelParameters, topKField);
    jfloat topP = env->GetFloatField(modelParameters, topPField);
    jfloat minP = env->GetFloatField(modelParameters, minPField);
    jint maxTokens = env->GetIntField(modelParameters, maxTokensField);
    jfloat repeatPenalty = env->GetFloatField(modelParameters, repeatPenaltyField);

    jstring systemPrompt_j = (jstring)env->GetObjectField(modelParameters, systemPromptField);
    const char* sp_c = env->GetStringUTFChars(systemPrompt_j, nullptr);
    std::string systemPromptStr(sp_c);
    env->ReleaseStringUTFChars(systemPrompt_j, sp_c);
    if (session->chatMessages.empty() && !systemPromptStr.empty()) {
        common_chat_msg sys_msg;
        sys_msg.role = "system";
        sys_msg.content = systemPromptStr;
        session->chatMessages.push_back(std::move(sys_msg));
    }

    session->sparams.temp = temperature;
    session->sparams.top_k = topK;
    session->sparams.top_p = topP;
    session->sparams.min_p = minP;
    session->sparams.penalty_repeat = repeatPenalty;

    const llama_model* model = session->model.get();
    llama_context* context = session->context.get();
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // Get user prompt
    const char *prompt_c = env->GetStringUTFChars(prompt_j, nullptr);
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(prompt_j, prompt_c);

    // Build the message with media marker
    const char* media_marker = mtmd_default_marker();
    std::string content_with_marker = std::string(media_marker) + "\n" + prompt_str;

    common_chat_msg user_msg;
    user_msg.role = "user";
    user_msg.content = content_with_marker;
    session->chatMessages.push_back(user_msg);

    // Apply chat template
    std::string formatted_prompt;
    bool thinking_forced_open = false;
    const bool wantThinkingMedia = (enableThinking == JNI_TRUE) && session->thinkingSupported;

    bool templateApplied = false;
    if (session->chatTemplates) {
        // Use Jinja for thinking-capable models (both to enable and disable thinking)
        if (session->thinkingSupported) {
            try {
                common_chat_templates_inputs inputs;
                inputs.messages = session->chatMessages;
                inputs.add_generation_prompt = true;
                inputs.use_jinja = true;
                inputs.enable_thinking = wantThinkingMedia;

                auto chat_params = common_chat_templates_apply(session->chatTemplates.get(), inputs);
                formatted_prompt = chat_params.prompt;
                thinking_forced_open = wantThinkingMedia ? chat_params.thinking_forced_open : false;
                if (!formatted_prompt.empty()) templateApplied = true;
            } catch (...) {
                // Jinja failed, fall through
            }
        }

        if (!templateApplied) {
            try {
                common_chat_templates_inputs inputs;
                inputs.messages = session->chatMessages;
                inputs.add_generation_prompt = true;
                inputs.use_jinja = false;
                inputs.enable_thinking = false;

                auto chat_params = common_chat_templates_apply(session->chatTemplates.get(), inputs);
                formatted_prompt = chat_params.prompt;
                thinking_forced_open = false;
                if (!formatted_prompt.empty()) templateApplied = true;
            } catch (...) {
                // Fall through to error
            }
        }
    }

    if (!templateApplied) {
        session->chatMessages.pop_back();
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur: impossible d'appliquer le template de chat pour le mode multimodal."));
        return;
    }

    // Create bitmap from image data
    jsize imageLen = env->GetArrayLength(imageData);
    jbyte* imageBytes = env->GetByteArrayElements(imageData, nullptr);

    mtmd_bitmap* bitmap = mtmd_helper_bitmap_init_from_buf(
        session->mtmdCtx,
        reinterpret_cast<const unsigned char*>(imageBytes),
        static_cast<size_t>(imageLen)
    );
    env->ReleaseByteArrayElements(imageData, imageBytes, JNI_ABORT);

    if (!bitmap) {
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur: Impossible de decoder l'image."));
        return;
    }

    // Tokenize with image
    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    mtmd_input_text input_text;
    input_text.text = formatted_prompt.c_str();
    input_text.add_special = true;
    input_text.parse_special = true;

    const mtmd_bitmap* bitmaps_arr[] = { bitmap };
    int32_t tokenize_result = mtmd_tokenize(session->mtmdCtx, chunks, &input_text, bitmaps_arr, 1);

    mtmd_bitmap_free(bitmap);

    if (tokenize_result != 0) {
        mtmd_input_chunks_free(chunks);
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur lors de la tokenisation multimodale."));
        return;
    }

    // Evaluate chunks (handles both text and image encoding)
    const int n_ctx = llama_n_ctx(context);
    llama_pos new_n_past = session->n_past;

    int32_t eval_result = mtmd_helper_eval_chunks(
        session->mtmdCtx, context, chunks,
        new_n_past, 0, n_ctx, true, &new_n_past
    );

    mtmd_input_chunks_free(chunks);

    if (eval_result != 0) {
        llama_memory_seq_rm(llama_get_memory(context), 0, saved_n_past, -1);
        session->n_past = saved_n_past;
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur lors de l'evaluation multimodale."));
        return;
    }

    // Generation loop (same as text-only predict, with thinking support)
    std::stringstream response_ss;
    std::stringstream thinking_ss;
    int n_cur = static_cast<int>(new_n_past);

    common_sampler *smpl = common_sampler_init(model, session->sparams);
    if (!smpl) {
        env->CallVoidMethod(callback_obj, on_error_method, env->NewStringUTF("Erreur d'initialisation du sampler."));
        return;
    }

    auto start_time = std::chrono::high_resolution_clock::now();
    int tokens_generated = 0;

    bool useThinkParsing = (enableThinking == JNI_TRUE) && session->thinkingSupported && thinking_forced_open;
    ThinkParseState thinkState = useThinkParsing ? ThinkParseState::INSIDE_THINK : ThinkParseState::OUTSIDE_THINK;
    std::string tagBuffer;
    static const std::string THINK_CLOSE_TAG = "</think>";

    llama_batch batch = llama_batch_init(n_ctx, 0, 1);

    // We need to sample from the last logits position
    // The eval_chunks already processed everything, so we sample directly
    for (int i = 0; i < maxTokens && n_cur < n_ctx; ++i) {
        if (session->cancelRequested.load()) {
            break;
        }

        const llama_token new_token_id = common_sampler_sample(smpl, context, -1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        common_sampler_accept(smpl, new_token_id, true);
        std::string piece = common_token_to_piece(context, new_token_id, true);

        if (!piece.empty()) {
            if (useThinkParsing) {
                for (size_t ci = 0; ci < piece.size(); ++ci) {
                    char ch = piece[ci];
                    switch (thinkState) {
                        case ThinkParseState::INSIDE_THINK:
                            if (ch == '<') {
                                tagBuffer.clear();
                                tagBuffer += ch;
                                thinkState = ThinkParseState::DETECTING_TAG;
                            } else {
                                thinking_ss << ch;
                                if (on_thinking_token_method) {
                                    std::string s(1, ch);
                                    jstring token_j = env->NewStringUTF(s.c_str());
                                    env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                    env->DeleteLocalRef(token_j);
                                }
                            }
                            break;
                        case ThinkParseState::DETECTING_TAG:
                            tagBuffer += ch;
                            if (tagBuffer.size() <= THINK_CLOSE_TAG.size()) {
                                if (THINK_CLOSE_TAG.compare(0, tagBuffer.size(), tagBuffer) == 0) {
                                    if (tagBuffer.size() == THINK_CLOSE_TAG.size()) {
                                        thinkState = ThinkParseState::OUTSIDE_THINK;
                                        tagBuffer.clear();
                                    }
                                } else {
                                    thinking_ss << tagBuffer;
                                    if (on_thinking_token_method) {
                                        jstring token_j = env->NewStringUTF(tagBuffer.c_str());
                                        env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                        env->DeleteLocalRef(token_j);
                                    }
                                    tagBuffer.clear();
                                    thinkState = ThinkParseState::INSIDE_THINK;
                                }
                            } else {
                                thinking_ss << tagBuffer;
                                if (on_thinking_token_method) {
                                    jstring token_j = env->NewStringUTF(tagBuffer.c_str());
                                    env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
                                    env->DeleteLocalRef(token_j);
                                }
                                tagBuffer.clear();
                                thinkState = ThinkParseState::INSIDE_THINK;
                            }
                            break;
                        case ThinkParseState::OUTSIDE_THINK:
                            response_ss << ch;
                            break;
                    }
                }
                if (thinkState == ThinkParseState::OUTSIDE_THINK) {
                    std::string fullResponse = response_ss.str();
                    static thread_local size_t lastResponseLen2 = 0;
                    if (i == 0) lastResponseLen2 = 0;
                    if (fullResponse.size() > lastResponseLen2) {
                        std::string responseChunk = fullResponse.substr(lastResponseLen2);
                        lastResponseLen2 = fullResponse.size();
                        if (!responseChunk.empty()) {
                            jstring token_j = env->NewStringUTF(responseChunk.c_str());
                            env->CallVoidMethod(callback_obj, on_token_method, token_j);
                            env->DeleteLocalRef(token_j);
                        }
                    }
                }
            } else {
                jstring token_j = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(callback_obj, on_token_method, token_j);
                env->DeleteLocalRef(token_j);
                response_ss << piece;
            }
        }

        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);

        if (llama_decode(context, batch) != 0) {
            break;
        }
        n_cur++;
        tokens_generated++;
    }

    if (!tagBuffer.empty() && on_thinking_token_method) {
        thinking_ss << tagBuffer;
        jstring token_j = env->NewStringUTF(tagBuffer.c_str());
        env->CallVoidMethod(callback_obj, on_thinking_token_method, token_j);
        env->DeleteLocalRef(token_j);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
    const long duration_count_seconds = duration.count() / 1000;
    const double tokens_per_second = (double)tokens_generated / (duration.count() / 1000.0);

    common_sampler_free(smpl);
    llama_batch_free(batch);

    std::string response_str = response_ss.str();
    std::string thinking_str = thinking_ss.str();

    if (session->cancelRequested.load()) {
        session->n_past = n_cur;
        if (!response_str.empty() || !thinking_str.empty()) {
            common_chat_msg assistant_msg;
            assistant_msg.role = "assistant";
            assistant_msg.content = response_str;
            assistant_msg.reasoning_content = thinking_str;
            session->chatMessages.push_back(std::move(assistant_msg));
        }
        return;
    }

    session->n_past = n_cur;

    common_chat_msg assistant_msg;
    assistant_msg.role = "assistant";
    assistant_msg.content = response_str;
    assistant_msg.reasoning_content = thinking_str;
    session->chatMessages.push_back(std::move(assistant_msg));

    env->CallVoidMethod(
        callback_obj,
        on_complete_method,
        tokens_per_second,
        duration_count_seconds
    );
}
