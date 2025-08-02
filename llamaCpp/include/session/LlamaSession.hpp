#ifndef LLAMA_SESSION_HPP
#define LLAMA_SESSION_HPP

#include "llama-cpp.h"
#include "llama.h"

struct LlamaSession {
    llama_model_ptr model;
    llama_context_ptr context;
};

#endif // LLAMA_SESSION_HPP