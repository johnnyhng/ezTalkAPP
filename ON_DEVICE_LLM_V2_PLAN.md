# On-device LLM v2 plan

Baseline: `v3.0` correction flow stays intact. Prompt behavior is intentionally unchanged in this branch until validation says otherwise.

## Phase status

1. v3.0 baseline + LLM diagnostic logs: done.
2. Shared Gemini provider abstraction + centralized request logging: done.
3. Settings page for LLM mode and Local Gemma management: done.
4. Local Gemma LiteRT-LM manual runtime: done.
5. App-level shared runtime + loading UX: done / current implementation.
6. Feature integration and A/B validation: in progress / current implementation.

## Current status snapshot

Date: 2026-06-27

Branch: `on-device-llm-v2`

Recent commits:

- `c731e182 Use empty Local Gemma model as cloud fallback`
- `89177eed Add cloud fallback action to Gemma loading`

Current Local Gemma model selection behavior:

- The default `selectedLocalGemmaModelName` is now empty.
- Empty model selection is a first-class sentinel meaning `(empty) Cloud LLM fallback`.
- The Settings Local Gemma dropdown always includes the empty fallback option.
- The empty fallback option cannot be deleted.
- If the selected Local Gemma model is empty:
  - app startup warm-up is skipped;
  - Local Gemma mode uses Cloud LLM fallback;
  - Auto mode uses Cloud LLM fallback unless another local model is selected;
  - Settings status shows Cloud LLM fallback.

Current loading UX:

- Local Gemma loading dialog includes `Continue with Cloud LLM`.
- Pressing the button immediately switches speaker LLM execution mode to Cloud.
- The active warm-up state is invalidated so stale LiteRT-LM success/failure callbacks are ignored by the UI.
- Native LiteRT-LM initialization may still finish in the background if already inside JNI, but the app no longer treats that stale result as active runtime state.

## Phase 4 LiteRT-LM solution

- Runtime provider: `LocalGemmaLitertLmLlmProvider`.
- Model layout: app-private files under `models/local_gemma/<modelName>/model.litertlm`.
- Legacy model layout still resolves `models/gemma4_e2b/model.litertlm`.
- Backend selection:
  - `auto`: try NPU, then GPU. CPU is disabled because it is too slow for the target UX.
  - `npu`: requires `libLiteRtDispatch_GoogleTensor.so` in `nativeLibraryDir`.
  - `gpu`: use LiteRT-LM GPU backend directly.
  - `cpu`: disabled.
  - Tensor/G5 compiled models are restricted to NPU. Auto does not fallback to GPU for those models because LiteRT GPU/OpenGL cannot run the Tensor-compiled graph.
  - In Auto mode, failed local initialization/generation falls back to Cloud LLM when Gemini is configured.
- Native dispatch library:
  - `app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_GoogleTensor.so`
  - Required for Pixel Tensor NPU dispatch.
- Manifest vendor runtime declarations:
  - `libedgetpu_litert.so`
  - `libOpenCL.so`
  - `libOpenCL-pixel.so`
  - `libvndksupport.so`
  - `libneuralnetworks.so`
  - These are optional `<uses-native-library>` entries so Android 12+ exposes published vendor runtimes to the app linker namespace when available.
- Provider selection:
  - Cloud mode: Gemini only.
  - Local Gemma mode: Local Gemma only; if the model file is missing, the provider is unavailable and logs the reason.
  - Auto mode: Local Gemma if the selected model exists; otherwise Gemini fallback.
- Loading behavior:
  - Phase 4 loaded LiteRT-LM lazily on first request.

## Phase 5 shared runtime solution

- Runtime singleton: `LocalGemmaRuntimeManager`.
- Provider factory and Speaker provider factory reuse the same Local Gemma provider for the same model path and backend.
- App startup warm-up:
  - Cloud mode: skip Local Gemma.
  - Local Gemma mode: warm up the selected local model and show a loading dialog while initialization runs.
  - Auto mode: warm up Local Gemma only if the selected local model exists; otherwise keep Gemini fallback without blocking startup.
- Error UX:
  - Missing model or runtime initialization failure is surfaced through an app-level dialog.
  - The user can continue or jump to Settings.
  - While loading, the user can switch to Cloud LLM and stop waiting for Local Gemma.
  - Empty model selection is not an error; it intentionally skips Local Gemma and uses Cloud LLM fallback.

## Phase 6 validation hooks

- `LlmRequestLogger.generateLogged` records request and response metrics for every LLM request.
- Completion metrics include provider, model, source, duration, success/failure, raw response length, finish reason, and token usage when available.
- Provider selection logs include execution mode, chosen provider, Gemini model, Local Gemma model, and Local Gemma backend.
- See `ON_DEVICE_LLM_VALIDATION.md` for the manual A/B checklist.

## Known constraints

- Tensor-compiled models such as `*_Google_Tensor_G5` should run on NPU. GPU/CPU fallback generally needs a non-Tensor LiteRT-LM model.
- First local request or app warm-up may be slow because LiteRT-LM engine initialization is expensive.
- The current prompt formatting follows the previous Local Gemma implementation and does not change the transcript correction prompt text.
- CPU execution remains disabled because observed latency is not acceptable for UX.
