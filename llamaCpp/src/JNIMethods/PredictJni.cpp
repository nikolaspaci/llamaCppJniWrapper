#include "JNIMethods/PredictJni.hpp"
#include "jni.h"
#include "common.h"
#include "sampling.h"
#include "llama-cpp.h"
#include "session/LlamaSession.hpp"
#include <string>
#include <vector>
#include <sstream> 

extern "C" JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(JNIEnv *env, jobject /* this */, jlong session_ptr, jstring prompt_j) {
  auto* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        return env->NewStringUTF("Erreur: Session invalide.");
    }

    const llama_model* model = session->model.get();
    llama_context* context = session->context.get();
    const llama_vocab* vocab = llama_model_get_vocab(model);
    
    // Convertit le prompt Java en chaîne C++
    const char *prompt_c = env->GetStringUTFChars(prompt_j, nullptr);
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(prompt_j, prompt_c);

    // tokenization of the prompt
    std::vector<llama_token> tokens = common_tokenize(vocab, prompt_str, true, true);
    
    if (tokens.empty()) {
        return env->NewStringUTF("Erreur lors de la tokenisation.");
    }

    // config of batch processing
    const int n_ctx = llama_n_ctx(context);
    const int n_batch = llama_n_batch(context);
    const int max_batch_size = std::min(n_batch, 512);

    const char* chat_template = llama_model_chat_template(model, nullptr);

    
    llama_batch batch = llama_batch_init(max_batch_size, 0, 1);

    // Processing the prompt in chunks with common_batch_add
    int processed_tokens = 0;
    const int n_tokens = tokens.size();
    while (processed_tokens < n_tokens) {
        const int chunk_size = std::min(max_batch_size, n_tokens - processed_tokens);

        // Clear the batch for this chunk
        common_batch_clear(batch);

        // Add the tokens from the chunk with common_batch_add
        for (int i = 0; i < chunk_size; ++i) {
            const int token_idx = processed_tokens + i;
            const bool need_logits = (processed_tokens + chunk_size == n_tokens) && (i == chunk_size - 1);
            
            common_batch_add(batch, tokens[token_idx], token_idx, {0}, need_logits);
        }

        // Evaluate this chunk
        if (llama_decode(context, batch) != 0) {
            llama_batch_free(batch);
            return env->NewStringUTF("Erreur lors de l'évaluation du prompt.");
        }
        
        processed_tokens += chunk_size;
    }

    // Configure the generation
    std::stringstream result_ss;
    const int max_new_tokens = 256;
    const llama_token eos_token = llama_vocab_eos(vocab);
    const llama_token bos_token = llama_vocab_bos(vocab);
    
    int n_cur = n_tokens;

    // Configure the sampling with common_sampler
  struct common_params_sampling sparams = {};
    
    common_sampler *smpl = common_sampler_init(model, sparams);
    if (!smpl) {
        llama_batch_free(batch);
        return env->NewStringUTF("Erreur d'initialisation du sampler.");
    }

    // Optimized generation loop
    for (int i = 0; i < max_new_tokens && n_cur < n_ctx - 1; ++i) {
        // Sampling with common_sampler
        const llama_token new_token_id = common_sampler_sample(smpl, context, batch.n_tokens - 1);

        // Stop checks
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        // Accept the token for the sampler's history
        common_sampler_accept(smpl, new_token_id, true);

        // Convert the token to a piece and append it to the result
        std::string piece = common_token_to_piece(context, new_token_id, true);
        result_ss << piece;

        // Efficiently prepare the batch for the next token with common_batch
        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);

        // Evaluate the new token
        if (llama_decode(context, batch) != 0) {
            break;
        }

        n_cur++;
        
        // Check if the memory position is at the maximum
        // This is to ensure we do not exceed the context size
        // and to avoid memory overflow
       llama_memory_t memory = llama_get_memory(context);
        const int memory_max_pos = llama_memory_seq_pos_max(memory, 0); // séquence 0
        if (memory_max_pos >= n_ctx - 1) {
            break;
        }
    }

    // Cleanup resources
    common_sampler_free(smpl);
    llama_batch_free(batch);
    
    return env->NewStringUTF(result_ss.str().c_str());
}