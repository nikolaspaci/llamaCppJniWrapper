#include "JNIMethods/PredictJni.hpp"
#include "jni.h"
#include "common.h"
#include "sampling.h"
#include "llama-cpp.h"
#include "session/LlamaSession.hpp"
#include <iostream>
#include <string>
#include <vector>
#include <sstream>
#include <cstring>
extern "C" JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(JNIEnv *env, jobject /* this */, jlong session_ptr, jstring prompt_j) {
    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        return env->NewStringUTF("Erreur: Session invalide.");
    }

    const llama_model* model = session->model.get();
    llama_context* context = session->context.get();
    const llama_vocab* vocab = llama_model_get_vocab(model);

    // 1. Add user message to history
    const char *prompt_c = env->GetStringUTFChars(prompt_j, nullptr);
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(prompt_j, prompt_c);
    session->messages.push_back(llama_chat_message{.role = strdup("user"), .content = strdup(prompt_str.c_str())});

    const char* key = "tokenizer.chat_template";

    std::vector<char> template_buffer(8192); // Allouer une taille généreuse. Les templates dépassent rarement 8ko.

    // Appel de la fonction de l'API
    int size = llama_model_meta_val_str(model, key, template_buffer.data(), 8192);

    int prompt_size = llama_chat_apply_template(template_buffer.data(), session->messages.data(), session->messages.size(), true,
                                           session->formattedMessages.data(), session->formattedMessages.size());

    session->formattedMessages.resize(prompt_size);
    int newP=llama_chat_apply_template(template_buffer.data(), session->messages.data(), session->messages.size(), true,
                                           session->formattedMessages.data(), session->formattedMessages.size());
    std::string formatted_prompt(session->formattedMessages.begin(), session->formattedMessages.begin() + prompt_size);

    // 3. Tokenize the full formatted prompt
    std::vector<llama_token> tokens = common_tokenize(vocab, formatted_prompt, true, true);

    if (tokens.empty()) {
        return env->NewStringUTF("Erreur lors de la tokenisation.");
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
        const int decodeId=llama_decode(context, batch);
        if (decodeId!= 0) {
            llama_batch_free(batch);
            return env->NewStringUTF("Erreur lors de l'évaluation du prompt.");
        }
        processed_tokens += chunk_size;
    }

    // Configure the generation
    std::stringstream result_ss;
    const int max_new_tokens = 256;
    int n_cur = n_tokens;

    struct common_params_sampling sparams = {};
    common_sampler *smpl = common_sampler_init(model, sparams);
    if (!smpl) {
        llama_batch_free(batch);
        return env->NewStringUTF("Erreur d'initialisation du sampler.");
    }

        auto start_time = std::chrono::high_resolution_clock::now();
    int tokens_generated = 0;

    // Generation loop
    for (int i = 0; i < max_new_tokens && n_cur < n_ctx; ++i) {
        const llama_token new_token_id = common_sampler_sample(smpl, context, batch.n_tokens - 1);

        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        common_sampler_accept(smpl, new_token_id, true);
        std::string piece = common_token_to_piece(context, new_token_id, true);
        result_ss << piece;

        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);

        if (llama_decode(context, batch) != 0) {
            break;
        }
        n_cur++;
        tokens_generated++;
    }
    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
    double tokens_per_second = (double)tokens_generated / (duration.count() / 1000.0);
    std::cout << "tokens_generated: " << tokens_generated << std::endl;
    std::cout << "Generation took " << duration.count() << " ms, " << tokens_per_second << " tokens/sec" << std::endl;

    session->n_past = n_cur;

    common_sampler_free(smpl);
    llama_batch_free(batch);

    // 6. Add assistant's response to history
    std::string result_str = result_ss.str();

    session->messages.push_back(llama_chat_message{.role = strdup("assistant"), .content = strdup(result_str.c_str())});
    // std::cout << "Assistant response: " << result_str << std::endl;
    jstring result_jstr = env->NewStringUTF(result_str.c_str());
    if (result_jstr == nullptr) {
        return env->NewStringUTF("Erreur lors de la création de la chaîne de résultat.");
    }
    return result_jstr;
}
