#include "JNIMethods/ReasoningStreamer.hpp"

#include "common.h"

ReasoningStreamer::ReasoningStreamer(JNIEnv *                   env,
                                     jobject                    callback_obj,
                                     jmethodID                  on_token,
                                     jmethodID                  on_thinking_token,
                                     const common_chat_params & chat_params,
                                     bool                       extract_reasoning)
    : env_(env),
      callback_obj_(callback_obj),
      on_token_(on_token),
      on_thinking_token_(on_thinking_token),
      extract_reasoning_(extract_reasoning) {
    if (extract_reasoning_) {
        parser_params_.format             = chat_params.format;
        parser_params_.generation_prompt  = chat_params.generation_prompt;
        parser_params_.reasoning_format   = COMMON_REASONING_FORMAT_DEEPSEEK;
        parser_params_.parse_tool_calls   = false;
        if (!chat_params.parser.empty()) {
            // chat_params.parser is the serialized PEG arena (std::string from
            // common_peg_arena::save()); load it into the in-memory arena.
            parser_params_.parser.load(chat_params.parser);
        }
    }
}

void ReasoningStreamer::emit(jmethodID method, const std::string & delta) {
    if (delta.empty() || method == nullptr) {
        return;
    }
    jstring s = env_->NewStringUTF(delta.c_str());
    env_->CallVoidMethod(callback_obj_, method, s);
    env_->DeleteLocalRef(s);
}

void ReasoningStreamer::emit_diffs(const common_chat_msg & cur) {
    auto diffs = common_chat_msg_diff::compute_diffs(prev_msg_, cur);
    for (const auto & d : diffs) {
        if (!d.reasoning_content_delta.empty()) {
            emit(on_thinking_token_, d.reasoning_content_delta);
        }
        if (!d.content_delta.empty()) {
            emit(on_token_, d.content_delta);
        }
    }
}

void ReasoningStreamer::feed(const std::string & piece) {
    if (piece.empty()) {
        return;
    }
    buffer_ += piece;

    if (!extract_reasoning_) {
        // Bypass: stream straight to content. buffer_ accumulates so finish()
        // returns the full string for history.
        emit(on_token_, piece);
        return;
    }

    common_chat_msg cur;
    try {
        cur = common_chat_parse(buffer_, /*is_partial=*/true, parser_params_);
    } catch (...) {
        // Partial parse may legitimately fail mid-tag — retry on next piece.
        return;
    }
    emit_diffs(cur);
    prev_msg_ = std::move(cur);
}

ReasoningStreamer::Result ReasoningStreamer::finish() {
    if (!extract_reasoning_) {
        return { buffer_, std::string() };
    }

    common_chat_msg final_msg;
    bool have_final = false;
    try {
        final_msg = common_chat_parse(buffer_, /*is_partial=*/false, parser_params_);
        have_final = true;
    } catch (...) {
        // Fall back to the last successful partial parse.
    }

    const common_chat_msg & cur = have_final ? final_msg : prev_msg_;
    emit_diffs(cur);
    return { cur.content, cur.reasoning_content };
}
