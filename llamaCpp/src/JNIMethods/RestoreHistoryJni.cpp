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
    jobjectArray messages
) {
    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session) {
        return;
    }

    session->messages.clear();

    jsize message_count = env->GetArrayLength(messages);
    for (jsize i = 0; i < message_count; ++i) {
        jobject chat_message_obj = env->GetObjectArrayElement(messages, i);
        jclass chat_message_class = env->GetObjectClass(chat_message_obj);

        jfieldID sender_field = env->GetFieldID(chat_message_class, "sender", "Lcom/nikolaspaci/app/llamallmlocal/data/database/Sender;");
        jobject sender_obj = env->GetObjectField(chat_message_obj, sender_field);
        jclass sender_class = env->GetObjectClass(sender_obj);
        jmethodID get_name_method = env->GetMethodID(sender_class, "name", "()Ljava/lang/String;");
        jstring sender_name_j = (jstring)env->CallObjectMethod(sender_obj, get_name_method);
        const char* sender_name_c = env->GetStringUTFChars(sender_name_j, nullptr);
        std::string role = (strcmp(sender_name_c, "USER") == 0) ? "user" : "assistant";
        env->ReleaseStringUTFChars(sender_name_j, sender_name_c);

        jfieldID message_field = env->GetFieldID(chat_message_class, "message", "Ljava/lang/String;");
        jstring message_j = (jstring)env->GetObjectField(chat_message_obj, message_field);
        const char* message_c = env->GetStringUTFChars(message_j, nullptr);

        session->messages.push_back({strdup(role.c_str()), strdup(message_c)});

        env->ReleaseStringUTFChars(message_j, message_c);
        env->DeleteLocalRef(chat_message_obj);
        env->DeleteLocalRef(sender_obj);
        env->DeleteLocalRef(sender_name_j);
    }
}
