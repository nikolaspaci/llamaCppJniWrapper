#include "JNIMethods/InitJni.hpp"
#include "session/LlamaSession.hpp"
#include "llama-cpp.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_init(JNIEnv *env, jobject /* this */, jstring modelPath, jobject modelParameters) {
    // Initialise the llama backend.
    llama_backend_init();

    // Find the ModelParameter class and its fields
    jclass modelParamsClass = env->FindClass("com/nikolaspaci/app/llamallmlocal/data/database/ModelParameter");
    jfieldID temperatureField = env->GetFieldID(modelParamsClass, "temperature", "F");
    jfieldID topKField = env->GetFieldID(modelParamsClass, "topK", "I");
    jfieldID topPField = env->GetFieldID(modelParamsClass, "topP", "F");
    jfieldID minPField = env->GetFieldID(modelParamsClass, "minP", "F");

    // Get the values from the modelParameters object
    jfloat temperature = env->GetFloatField(modelParameters, temperatureField);
    jint topK = env->GetIntField(modelParameters, topKField);
    jfloat topP = env->GetFloatField(modelParameters, topPField);
    jfloat minP = env->GetFloatField(modelParameters, minPField);


    // Prepare the parameters for the model and context.
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true; // Use memory-mapped files for model loading.
    model_params.use_mlock = false;
    llama_context_params ctx_params = llama_context_default_params();

    // You can adjust parameters here if needed. For example:
    ctx_params.n_ctx = 2048; // Context size.
    ctx_params.n_threads = 4; // Number of CPU threads to use.
    ctx_params.no_perf = true; // Disable performance monitoring.

    // Create a session on the heap
    auto* session = new LlamaSession();
    session->sparams.temp = temperature;
    session->sparams.top_k = topK;
    session->sparams.top_p = topP;
    session->sparams.min_p = minP;


    // Load the model and assign the raw pointer to the unique_ptr
    const char *path = env->GetStringUTFChars(modelPath, 0);
    session->model.reset(llama_model_load_from_file(path, model_params));
    env->ReleaseStringUTFChars(modelPath, path);

    if (!session->model) {
        delete session; // Cleanup the session if the model failed to load
        llama_backend_free();
        return 0;
    }

    // Create the context and assign it to the unique_ptr
    session->context.reset(llama_init_from_model(session->model.get(), ctx_params));
    if (!session->context) {
        delete session; // The model's unique_ptr will be automatically released here
        llama_backend_free();
        return 0;
    }

    // Return the pointer to the session
    return reinterpret_cast<jlong>(session);
}