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

Empty Local Gemma model selection:

```text
Local Gemma warm-up skipped: empty model selection uses Cloud LLM fallback
LLM provider selected mode=<local_gemma_litert_lm|auto_local> provider=gemini ...
```

Loading dialog Cloud fallback:

```text
Local Gemma warm-up cancelled by user; switching speaker LLM execution mode to cloud
Ignoring stale Local Gemma warm-up <success|failure> ...
LLM provider selected mode=cloud provider=gemini ...
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
5. Local Gemma mode with empty model selected: should skip local warm-up and use Cloud LLM.
6. Local Gemma loading dialog: press Continue with Cloud LLM; UI should leave loading state and Settings should show Cloud mode.
7. Translate LLM correction.
8. Experiment Zhuyin suggestions.
9. Speaker semantic fallback.

Prompt text should remain unchanged across Cloud and Local Gemma runs unless explicitly changed in a later prompt-tuning phase.
