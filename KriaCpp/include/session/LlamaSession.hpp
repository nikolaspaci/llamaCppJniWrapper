#ifndef LLAMA_SESSION_HPP
#define LLAMA_SESSION_HPP

#include <memory>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include "llama-cpp.h"
#include "llama.h"
#include "common.h"
#include "chat.h"
#include "mtmd.h"
#include "mtmd-helper.h"

struct LlamaSession {
    llama_model_ptr model;
    llama_context_ptr context;

    // Chat templates and messages (using C++ strings - no manual strdup/free)
    common_chat_templates_ptr chatTemplates;
    std::vector<common_chat_msg> chatMessages;

    int n_past = 0;
    common_params_sampling sparams;
    std::atomic<bool> cancelRequested{false};
    std::mutex predictMutex;

    // Thinking support
    bool thinkingSupported = false;

    // Multimodal
    mtmd_context* mtmdCtx = nullptr;
    bool hasVision = false;

    ~LlamaSession() {
        if (mtmdCtx) {
            mtmd_free(mtmdCtx);
            mtmdCtx = nullptr;
        }
    }
};

#endif // LLAMA_SESSION_HPP
