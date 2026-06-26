# On-device LLM validation checklist

Use the `ezTalk-LLM` tag for A/B validation between Gemini and Local Gemma.

## Logcat filter

```bash
adb logcat -s ezTalk-LLM eztalk
```

## Expected provider-selection logs

Cloud:

```text
LLM provider selected mode=cloud provider=gemini ...
```

Local Gemma:

```text
LLM provider selected mode=local_gemma_litert_lm provider=local_gemma_litertlm ...
Local Gemma shared warm-up start ...
Local Gemma shared warm-up ready ...
```

Auto with a local model present:

```text
LLM provider selected mode=auto_local provider=local_gemma_litertlm ...
```

Auto without a local model:

```text
Local Gemma warm-up skipped in auto mode ...
LLM provider selected mode=auto_local provider=gemini ...
```

## Expected request/response metrics

Every LLM request should emit:

```text
LLM request source=<source> provider=<provider> ...
LLM response source=<source> provider=<provider> durationMs=<ms> success=<true|false> rawLength=<n> ...
```

Compare these fields for A/B:

- `source`: correction, translation, zhuyin, speaker semantic.
- `provider`: `gemini` vs `local_gemma_litertlm`.
- `durationMs`: end-to-end generation latency.
- `success`: request success/failure.
- `rawLength`: empty or very short outputs usually indicate bad local generation.
- correction module logs: final correction confidence and replacement/skipped reason.

## Manual test matrix

1. Cloud mode + Home LLM correction.
2. Local Gemma mode + Home LLM correction.
3. Auto mode with local model present.
4. Auto mode with selected local model missing.
5. Translate LLM correction.
6. Experiment Zhuyin suggestions.
7. Speaker semantic fallback.

Prompt text should remain unchanged across Cloud and Local Gemma runs unless explicitly changed in a later prompt-tuning phase.
