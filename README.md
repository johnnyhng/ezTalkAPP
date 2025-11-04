# ezTalkAPP

ezTalkAPP is a mobile application for Android that provides speech recognition capabilities. This app is built with Kotlin and utilizes the Sherpa-ONNX and SenseVoice libraries for its core functionalities.

## Features

*   Real-time speech-to-text transcription
*   Offline and streaming ASR capabilities
*   Easy-to-use interface for managing models and settings
*   Waveform display for visualizing audio input

## Installation

To build and install the app, follow these steps:

1.  Clone the repository:
    ```bash
    git clone https://github.com/johnnyhng/ezTalkAPP.git
    ```
2.  The `jniLibs` and `assets` directories are not included in this repository. Please refer to the [sherpa-onnx](httpshttps://github.com/k2fsa/sherpa-onnx) repository to obtain the necessary files for these directories.
3.  Open the project in Android Studio.
4.  Build the project and run it on an Android device or emulator.

## Usage

Once the app is installed, you can start using it for speech recognition. The main screen provides an interface to start and stop recording, and the recognized text will be displayed in real-time. You can manage the ASR models and other settings in the settings screen.

## References

This project is based on the following open-source projects:

*   **Sherpa-ONNX**: [https://github.com/k2fsa/sherpa-onnx](https://github.com/k2fsa/sherpa-onnx)
*   **SenseVoice**: [https://github.com/FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice)

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue to discuss any changes.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
