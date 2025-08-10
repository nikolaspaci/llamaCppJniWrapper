# Llama.cpp Android JNI Wrapper

This project provides a complete, high-performance solution for running GGUF-formatted Large Language Models (LLMs) locally on Android devices. It leverages the power of `llama.cpp` by wrapping it in a JNI layer, making it accessible from a modern Kotlin application with a Jetpack Compose UI.

## Key Features

- **Optimized Native Performance**: Directly utilizes `llama.cpp` for efficient model inference on the CPU.
- **Dynamic CPU Architecture Detection**: Automatically builds and loads the most performant native library (`armv9-a` or `armv8.2-a`) at runtime for `arm64-v8a` devices.
- **Modern Android UI**: A clean, responsive chat interface built entirely with Jetpack Compose.
- **Real-time Streaming**: Displays model responses token-by-token as they are generated.
- **Conversation History**: Saves and lists all conversations using a local Room database.
- **Dynamic Model Selection**: Allows users to load new `*.gguf` models from their device storage, which are then cached and managed by the app.
- **Asynchronous Operations**: All heavy tasks like model loading, file copying, and inference are handled in the background to keep the UI smooth.

## Project Structure

The project is a monorepo composed of two main subprojects:

1.  `llamaCpp`: A C++ project that acts as the JNI (Java Native Interface) bridge. It includes `llama.cpp` as a submodule and compiles the core logic into shared native libraries (`.so` files) that the Android app can load.
2.  `LlamaLlmLocal`: A standard Android application written in Kotlin. It contains all the UI (Jetpack Compose), application logic (ViewModels), database management (Room), and the JNI declarations to communicate with the native layer.

---

## How It Works: Dynamic Library Loading

To maximize performance, the project compiles two distinct versions of the native library for the `arm64-v8a` architecture, each targeting a different instruction set:
- `libjniLlamaCppWrapper_armv9-a.so`: Optimized for modern ARMv9 CPUs.
- `libjniLlamaCppWrapper_v82a.so`: A more compatible version for older ARMv8.2 CPUs.

The application intelligently loads the best library at runtime. It first attempts to load the `v9-a` library. If an `UnsatisfiedLinkError` occurs (which happens if the device's CPU does not support the required instructions), it gracefully falls back to the more compatible `v8.2-a` library. This ensures maximum performance on capable devices without sacrificing compatibility.

## Getting Started

### Prerequisites

- Android Studio (latest version recommended)
- Android NDK (installable via the SDK Manager in Android Studio)
- CMake (installable via the SDK Manager in Android Studio)

### Build Instructions

1.  **Clone the Repository**
    ```bash
    git clone --recurse-submodules https://your-repository-url.git
    cd your-repository-url
    ```
    If you have already cloned the project without the submodules, you can initialize them with:
    ```bash
    git submodule update --init --recursive
    ```

2.  **Open the Android Project**
    - Open Android Studio.
    - Select "Open" and navigate to the `LlamaLlmLocal` directory inside the cloned repository.

3.  **Sync and Build**
    - Android Studio will automatically detect the Gradle project and start the sync process.
    - The Android Gradle plugin is configured to trigger the CMake build for the `llamaCpp` subproject. It will compile the native libraries for all targeted ABIs.
    - Once the Gradle sync is complete, you can build and run the application on a connected Android device or emulator.

## Usage

1.  Launch the application.
2.  On the main screen, click "Select a Model".
3.  If you have no models cached, select "Browse for new model..." and choose a `*.gguf` file from your device's storage.
4.  The model will be copied to the app's cache, and a loading indicator will be displayed. Once loaded, its name will appear on the button.
5.  Type a message in the input field and start chatting with the model.

---

## Contributing

Contributions are welcome! Please feel free to submit a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.