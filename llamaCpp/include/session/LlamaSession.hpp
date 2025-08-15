#ifndef LLAMA_SESSION_HPP
#define LLAMA_SESSION_HPP

#include <memory>
#include <string>
#include <vector>
#include "llama-cpp.h"
#include "llama.h"
#include "common.h"
#include <cstdlib>  // Add this for free()


struct LlamaSession {
    llama_model_ptr model;
    llama_context_ptr context;
    std::vector<llama_chat_message> messages;
    std::vector<char> formattedMessages;
    int n_past = 0;  // Pour tracker la position dans le contexte
    common_params_sampling sparams;

    //need to delete llama_chat_message content
    ~LlamaSession() {
        for (auto& msg : messages) {
            // Only free if these were malloc'd
            if (msg.role) std::free(const_cast<char*>(msg.role));
            if (msg.content) std::free(const_cast<char*>(msg.content));
        }
    }
};

#endif // LLAMA_SESSION_HPP