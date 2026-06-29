# ezTalkAPP

ezTalkAPP is an Android app for speech recognition, target-speaker extraction,
and speaker-assist workflows. It is built with Kotlin and uses Sherpa-ONNX,
SenseVoice, ONNX Runtime, and optional on-device LiteRT-LM components.

## Features

* Real-time speech-to-text transcription
* Offline and streaming ASR capabilities
* Target Speaker Extraction (TSE) preprocessing
* Optional local Gemma LiteRT-LM speaker assistance
* Model and settings management
* Waveform display for visualizing audio input

## Installation

To build and install the app, follow these steps:

1. Clone the repository:

   ```sh
   git clone https://github.com/johnnyhng/ezTalkAPP.git
   cd ezTalkAPP
   ```

2. Download the Android native library release bundle from the project GitHub
   release page and install it under:

   ```text
   app/src/main/jniLibs/arm64-v8a/
   ```

   See [app/src/main/jniLibs/README.md](app/src/main/jniLibs/README.md) for the
   expected `.so` files and troubleshooting notes.

3. Download the Android asset release bundle from the project GitHub release
   page and install it under:

   ```text
   app/src/main/assets/
   ```

   See [app/src/main/assets/README.md](app/src/main/assets/README.md) for the
   expected model and asset paths.

4. Open the project in Android Studio, or build from the command line:

   ```sh
   ./gradlew assembleDebug
   ```

5. Run the app on an Android device or emulator.

## Usage

Once the app is installed, you can start using it for speech recognition. The main screen provides an interface to start and stop recording, and the recognized text will be displayed in real-time. You can manage the ASR models and other settings in the settings screen.

## References

This project is based on or references the following open-source projects and initiatives:

* **Sherpa-ONNX**: [https://github.com/k2fsa/sherpa-onnx](https://github.com/k2fsa/sherpa-onnx)
* **SenseVoice**: [https://github.com/FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice)
* **Google Project VOICE**: [https://github.com/google/project-voice](https://github.com/google/project-voice)
  for the Traditional Chinese Zhuyin auxiliary input workflow

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue to discuss any changes.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
