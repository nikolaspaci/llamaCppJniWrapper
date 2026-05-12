#ifndef WHISPER_SESSION_HPP
#define WHISPER_SESSION_HPP

#include <atomic>
#include <mutex>
#include "whisper.h"

struct WhisperSession {
    whisper_context* ctx = nullptr;
    std::atomic<bool> cancelRequested{false};
    std::mutex transcribeMutex;
    int n_threads = 4;

    ~WhisperSession() {
        if (ctx) {
            whisper_free(ctx);
            ctx = nullptr;
        }
    }
};

#endif // WHISPER_SESSION_HPP
