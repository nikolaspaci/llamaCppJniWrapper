#pragma once

#include <deque>
#include <mutex>
#include <string>
#include <cstring>

namespace kria {

// Thread-safe ring buffer that keeps the last N native log lines (ggml/llama.cpp).
// Lines are read from Kotlin via JNI when an error is reported, so the Firestore
// payload contains the recent native context preceding the failure.
class NativeLogBuffer {
public:
    static constexpr std::size_t MAX_LINES = 300;
    static constexpr std::size_t MAX_LINE_LENGTH = 1024;

    static NativeLogBuffer& instance() {
        static NativeLogBuffer inst;
        return inst;
    }

    void push(const char* level, const char* tag, const char* text) {
        if (text == nullptr) return;
        std::string line;
        line.reserve(64 + std::strlen(text));
        line.append(level).append("/").append(tag).append(": ").append(text);
        // Trim trailing newlines so the buffer reads cleanly.
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) {
            line.pop_back();
        }
        if (line.size() > MAX_LINE_LENGTH) {
            line.resize(MAX_LINE_LENGTH);
        }
        std::lock_guard<std::mutex> lock(mu_);
        buffer_.push_back(std::move(line));
        while (buffer_.size() > MAX_LINES) {
            buffer_.pop_front();
        }
    }

    std::string dump() const {
        std::lock_guard<std::mutex> lock(mu_);
        std::string out;
        for (const auto& l : buffer_) {
            out.append(l).push_back('\n');
        }
        return out;
    }

private:
    NativeLogBuffer() = default;
    mutable std::mutex mu_;
    std::deque<std::string> buffer_;
};

} // namespace kria
