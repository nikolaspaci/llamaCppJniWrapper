#include <iostream>
#include <string>
#include <vector>
#include <cassert>

// The real jni.h needs to be included for the types.
#include <jni.h>

// Headers for the functions to be tested
#include "JNIMethods/InitJni.hpp"
#include "JNIMethods/PredictJni.hpp"
#include "JNIMethods/FreeJni.hpp"

// --- Mock JNI Environment ---

// Helper functions to handle jstring in our test
const char* mock_jstring_to_c_str(jstring str) {
    return reinterpret_cast<const char*>(str);
}

jstring mock_c_str_to_jstring(const char* str) {
    return reinterpret_cast<jstring>(const_cast<char*>(str));
}

// Mock implementations for JNI functions that our code uses
extern "C" const char* mock_GetStringUTFChars(JNIEnv *env, jstring str, jboolean* isCopy) {
    if (isCopy) *isCopy = JNI_FALSE;
    return mock_jstring_to_c_str(str);
}

extern "C" void mock_ReleaseStringUTFChars(JNIEnv *env, jstring str, const char* utf) {
    // This is a no-op in our mock environment.
}

extern "C" jstring mock_NewStringUTF(JNIEnv* env, const char* bytes) {
    // In a real JNI env, this would create a new Java String object.
    // Here, we just cast, assuming the C-string lives long enough for the test.
    return mock_c_str_to_jstring(bytes);
}

// The real JNIEnv is a pointer to a struct (_JNIEnv) which contains a pointer 
// to a function table (JNINativeInterface_). We need to construct a fake one.
static JNINativeInterface_ jni_function_table;

// A struct that has the same memory layout as JNIEnv in C++
struct MockJNIEnv {
    const JNINativeInterface_* functions;
};

// --- Test Main ---

int main(int argc, char **argv) {
    // Set up our mock JNI environment
    jni_function_table.GetStringUTFChars = mock_GetStringUTFChars;
    jni_function_table.ReleaseStringUTFChars = mock_ReleaseStringUTFChars;
    jni_function_table.NewStringUTF = mock_NewStringUTF;

    MockJNIEnv mock_env;
    mock_env.functions = &jni_function_table;
    
    JNIEnv* env = reinterpret_cast<JNIEnv*>(&mock_env);
    jobject mock_this = nullptr;

    std::cout << "Starting JNI methods test..." << std::endl;

    if (argc < 2) {
        std::cerr << "ERROR: Model path not provided." << std::endl;
        std::cerr << "Usage: " << argv[0] << " /path/to/your/model.gguf" << std::endl;
        return 1;
    }
    const char* model_path_c_str = argv[1];
    jstring model_path_jstr = mock_c_str_to_jstring(model_path_c_str);

    std::cout << "Attempting to initialize with model: " << model_path_c_str << std::endl;

    // 1. Test init
    jlong session_ptr = Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_init(env, mock_this, model_path_jstr);
    
    if (session_ptr == 0) {
        std::cerr << "TEST FAILED: init() returned a null session pointer." << std::endl;
        std::cerr << "Please check that the model path is correct and the file is accessible." << std::endl;
        return 1;
    }
    std::cout << "SUCCESS: init() test passed. Session pointer: " << session_ptr << std::endl;

    // 2. Test predict
    const char* prompt_c_str = "Who is the current president of France?";
    jstring prompt_jstr = mock_c_str_to_jstring(prompt_c_str);

    std::cout << "\nTesting predict() with prompt: \"" << prompt_c_str << "\"" << std::endl;
    jstring result_jstr = Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(env, mock_this, session_ptr, prompt_jstr);
    
    if (result_jstr == nullptr) {
        std::cerr << "TEST FAILED: predict() returned a null string." << std::endl;
        Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_free(env, mock_this, session_ptr);
        return 1;
    }

    const char* result_c_str = mock_jstring_to_c_str(result_jstr);
    std::string result_str(result_c_str);
    
    if (result_str.find("Erreur") != std::string::npos || result_str.empty()) {
         std::cerr << "TEST FAILED: predict() returned an error or empty string: " << result_str << std::endl;
         Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_free(env, mock_this, session_ptr);
         return 1;
    }

    std::cout << "SUCCESS: predict() test passed. Response: " << result_str << std::endl;

    // 3. Test free
    std::cout << "\nTesting free()..." << std::endl;
    Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_free(env, mock_this, session_ptr);
    std::cout << "SUCCESS: free() test passed." << std::endl;

    std::cout << "\nAll JNI method tests passed successfully!" << std::endl;

    return 0;
}