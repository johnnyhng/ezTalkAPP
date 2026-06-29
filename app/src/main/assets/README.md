# Android Asset Install Guide

Large Android runtime assets are intentionally not committed to this repository.
Download the Android asset release bundle from GitHub and install the files into
this directory before building or running the app.

## Install Location

Install release assets under:

```text
app/src/main/assets/
```

Keep directory names and filenames exactly as released. Android asset lookups
are path-sensitive.

## Required Runtime Assets

The current default app flow expects these assets:

```text
app/src/main/assets/custom-sense-voice/model.int8.onnx
app/src/main/assets/custom-sense-voice/tokens.txt
app/src/main/assets/silero_vad.onnx
app/src/main/assets/transformer_energy_64d_1L_int8.onnx
app/src/main/assets/dvector.bin
app/src/main/assets/models/universal_sentence_encoder.tflite
```

`NativeTseWaveformPipeline` also supports this newer TSE model asset name when
present:

```text
app/src/main/assets/soul_filter_eat_int8.onnx
```

If `soul_filter_eat_int8.onnx` is not present, the offline TSE pipeline falls
back to `transformer_energy_64d_1L_int8.onnx`. The realtime TSE preprocessor
still uses `transformer_energy_64d_1L_int8.onnx` and `dvector.bin` by default.

## Install From GitHub Release

1. Download the Android asset release bundle from the project GitHub release
   page.
2. Extract the bundle.
3. Copy the extracted files and directories into:

   ```text
   app/src/main/assets/
   ```

4. Verify the expected paths exist:

   ```sh
   find app/src/main/assets -maxdepth 3 -type f | sort
   ```

5. Build the app:

   ```sh
   ./gradlew compileDebugKotlin
   ```

## Optional And Legacy Assets

The app may contain or download other model files for experiments, older TSE
paths, or local validation. Do not include these in the default GitHub asset
bundle unless the release notes say they are required:

```text
app/src/main/assets/dvector_64d.bin
app/src/main/assets/dvector_64d_norm.bin
app/src/main/assets/transformer_64d_fp32.onnx
app/src/main/assets/transformer_64d_int8.onnx
app/src/main/assets/transformer_energy_16d_1L_int8.onnx
app/src/main/assets/transformer_energy_16d_int8.onnx
app/src/main/assets/transformer_energy_64d_int8.onnx
app/src/main/assets/voice_filter*.onnx
app/src/main/assets/voice_filter*.tflite
```

Local Gemma `.litertlm` files are not installed here. They are downloaded or
imported by the app at runtime.

## Notes

- Assets are ignored by Git in this directory, so installing or replacing them
  will not show up in `git status`.
- Missing ASR assets usually fail when `custom-sense-voice/model.int8.onnx` or
  `custom-sense-voice/tokens.txt` cannot be opened.
- Missing TSE assets usually fail when `transformer_energy_64d_1L_int8.onnx`,
  `soul_filter_eat_int8.onnx`, or `dvector.bin` cannot be opened.
- Missing VAD assets usually fail when `silero_vad.onnx` cannot be opened.
