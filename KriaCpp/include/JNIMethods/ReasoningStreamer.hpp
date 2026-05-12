#ifndef REASONING_STREAMER_HPP
#define REASONING_STREAMER_HPP

#include <jni.h>
#include <string>

#include "chat.h"

// Streams model output to two JNI callbacks (content vs reasoning_content)
// using llama.cpp's common_chat_parse in partial mode. The split rule comes
// from the chat template's PEG parser (so it works uniformly for <think>,
// [THINK], <|channel|>analysis<|message|>, etc.).
//
// If extract_reasoning is false, the streamer bypasses the parser entirely
// and forwards every piece to on_token unchanged. This keeps the no-thinking
// path overhead-free.
class ReasoningStreamer {
public:
    ReasoningStreamer(JNIEnv * env,
                      jobject callback_obj,
                      jmethodID on_token,
                      jmethodID on_thinking_token,
                      const common_chat_params & chat_params,
                      bool extract_reasoning);

    void feed(const std::string & piece);

    struct Result {
        std::string content;
        std::string reasoning_content;
    };
    Result finish();

private:
    JNIEnv *  env_;
    jobject   callback_obj_;
    jmethodID on_token_;
    jmethodID on_thinking_token_;

    bool                       extract_reasoning_;
    common_chat_parser_params  parser_params_;
    std::string                buffer_;
    common_chat_msg            prev_msg_;

    void emit(jmethodID method, const std::string & delta);
    void emit_diffs(const common_chat_msg & cur);
};

#endif // REASONING_STREAMER_HPP
