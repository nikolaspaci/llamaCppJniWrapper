#include "JNIMethods/RestoreHistoryJni.hpp"
#include "session/LlamaSession.hpp"
#include "llama-cpp.h"
#include <string>
#include <vector>

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_restoreHistory(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr,
    jobjectArray messages,
    jstring systemPrompt_j
) {
    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        return;
    }

    session->chatMessages.clear();

    if (session->context) {
        llama_memory_seq_rm(llama_get_memory(session->context.get()), 0, 0, -1);
    }
    session->n_past = 0;

    // Inject system prompt as the first message if non-empty
    const char* sp = env->GetStringUTFChars(systemPrompt_j, nullptr);
    std::string systemPromptStr(sp);
    env->ReleaseStringUTFChars(systemPrompt_j, sp);
    if (!systemPromptStr.empty()) {
        common_chat_msg sys_msg;
        sys_msg.role = "system";
        sys_msg.content = systemPromptStr;
        session->chatMessages.push_back(std::move(sys_msg));
    }

    jsize message_count = env->GetArrayLength(messages);
    for (jsize i = 0; i < message_count; ++i) {
        jobject chat_message_obj = env->GetObjectArrayElement(messages, i);
        jclass chat_message_class = env->GetObjectClass(chat_message_obj);

        // Get sender (USER or BOT -> "user" or "assistant")
        jfieldID sender_field = env->GetFieldID(chat_message_class, "sender", "Lcom/nikolaspaci/app/llamallmlocal/data/database/Sender;");
        jobject sender_obj = env->GetObjectField(chat_message_obj, sender_field);
        jclass sender_class = env->GetObjectClass(sender_obj);
        jmethodID get_name_method = env->GetMethodID(sender_class, "name", "()Ljava/lang/String;");
        jstring sender_name_j = (jstring)env->CallObjectMethod(sender_obj, get_name_method);
        const char* sender_name_c = env->GetStringUTFChars(sender_name_j, nullptr);
        std::string role = (strcmp(sender_name_c, "USER") == 0) ? "user" : "assistant";
        env->ReleaseStringUTFChars(sender_name_j, sender_name_c);

        // Get message content
        jfieldID message_field = env->GetFieldID(chat_message_class, "message", "Ljava/lang/String;");
        jstring message_j = (jstring)env->GetObjectField(chat_message_obj, message_field);
        const char* message_c = env->GetStringUTFChars(message_j, nullptr);

        common_chat_msg msg;
        msg.role = role;
        msg.content = std::string(message_c);

        // Get thinkingContent if available (for assistant messages)
        if (role == "assistant") {
            jfieldID thinking_field = env->GetFieldID(chat_message_class, "thinkingContent", "Ljava/lang/String;");
            if (thinking_field != nullptr) {
                jstring thinking_j = (jstring)env->GetObjectField(chat_message_obj, thinking_field);
                if (thinking_j != nullptr) {
                    const char* thinking_c = env->GetStringUTFChars(thinking_j, nullptr);
                    msg.reasoning_content = std::string(thinking_c);
                    env->ReleaseStringUTFChars(thinking_j, thinking_c);
                }
            } else {
                // Field not found - clear any pending exception
                env->ExceptionClear();
            }
        }

        session->chatMessages.push_back(std::move(msg));

        env->ReleaseStringUTFChars(message_j, message_c);
        env->DeleteLocalRef(chat_message_obj);
        env->DeleteLocalRef(sender_obj);
        env->DeleteLocalRef(sender_name_j);
    }
}
