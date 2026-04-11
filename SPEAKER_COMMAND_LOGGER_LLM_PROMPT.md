codex

# Speaker Command, Logger, and LLM Prompt

This document summarizes the current content-speech control flow in the Speaker feature, with emphasis on:

- how ASR text becomes a playback command
- what is logged along the way
- how the LLM fallback prompt is constructed

The description here reflects the current implementation on the `enhance-cmd` branch.

## 1. High-Level Flow

For content speech mode, the runtime path is:

1. Local ASR produces partial/final transcripts during one countdown window.
2. `SpeakerAsrController` aggregates transcript variants into one utterance bundle.
3. `SpeakerScreen` receives the finalized utterance bundle for the `CONTENT` target.
4. `handleSpeakerContentAsr(...)` first tries deterministic command resolution across all utterance variants.
5. If command resolution fails, semantic search runs on indexed content chunks using the utterance primary text.
6. If semantic search still returns `NoMatch`, optional LLM fallback is attempted using the aggregated utterance variants plus explicit command/line action space.
7. The final result is mapped back to either:
   - playback of the whole document
   - playback of a line
   - candidate highlighting
   - no-op / feedback toast

Main entry points:

- [SpeakerScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt)
- [SpeakerContentAsrActions.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentAsrActions.kt)
- [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt)

## 2. Content Line Generation

The Speaker content flow now uses a shared line-splitting helper:

- [SpeakerContentLines.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentLines.kt)

Current splitting rules:

- normalize `\r\n` to `\n`
- split on newline `\n`
- split on `。`
- split on `.`
- trim each segment
- drop blank segments

This shared helper is used by:

- [SpeakerContentScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerContentScreen.kt)
- [SpeakerScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt)
- [SpeakerSemanticIndexer.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticIndexer.kt)

This keeps visible list items, command line indices, and semantic chunk indexing aligned.

## 3. Command Generation Logic

Deterministic content command parsing is implemented in:

- [SpeakerCommandResolver.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerCommandResolver.kt)

### 3.1 Supported Commands

The current sealed command model is:

- `Play`
- `Pause`
- `Stop`
- `PlayLine(lineIndex)`

### 3.2 Normalization

Before matching, ASR text is normalized by `normalizeSpeakerCommandText(...)`.

Current normalization includes:

- trim
- remove whitespace
- remove punctuation such as `，。、「」？！：；,.!?`
- normalize some common ASR variants:
  - `撥` -> `播`
  - `拨` -> `播`
  - `續` -> `续`
  - `繼` -> `继`
  - `暫` -> `暂`
- normalize common suffix misrecognitions for `行`:
  - `航` -> `行`
  - `杭` -> `行`
  - `型` -> `行`
  - `項` / `项` -> `行`
  - `橫` / `横` -> `行`
  - `號` / `号` -> `行`

### 3.3 Line Command Resolution

Line-based commands are resolved first.

Supported forms:

- `第3行`
- `第三行`
- `3行`
- `三行`

If a line number is recognized:

1. it is converted to zero-based index
2. bounds are checked against the currently available content lines
3. blank lines are rejected
4. successful match becomes `PlayLine(lineIndex)`

If text still contains `行` but no valid line number can be resolved, the resolver returns `null`.

This is a deliberate guard to avoid accidentally interpreting partial line requests as a generic `Play`.

### 3.4 Generic Playback Command Resolution

If no line command matched, generic command keywords are checked.

If the primary text does not resolve, the runtime now retries command resolution across the whole utterance variant list gathered during the same countdown window.

Current keyword groups are intentionally narrow:

`Play`

- `播放`
- `开始播放`
- `開始播放`
- `继续播放`
- `繼續播放`
- `續播`

`Pause`

- `暂停`
- `暫停`
- `暂停播放`
- `暫停播放`

`Stop`

- `停止`
- `停播`
- `停止播放`

Beyond those exact keywords, softer phrasing now falls through to example-based semantic intent matching in the resolver, for example:

- `念給我聽`
- `先停一下`
- `不要播了`

## 4. Command Execution Logic

Execution logic lives in:

- [SpeakerContentAsrActions.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentAsrActions.kt)

The main function is:

- `handleSpeakerContentAsr(...)`

It works in this order:

1. call `handleSpeakerContentCommand(...)`
2. if command matched, execute command and return
3. otherwise continue to semantic resolution

### 4.1 Command Behavior

`Play`

- clears semantic UI state
- calls `playDocumentWithAsrStop(document)`
- shows toast if TTS is not ready or content is empty

`PlayLine`

- clears semantic UI state
- calls `playLineWithAsrStop(document, lineIndex, lineText)`
- shows toast if TTS is not ready or content is empty

`Pause`

- clears semantic UI state
- only pauses if the selected document is actually playing
- otherwise logs that the pause was ignored

`Stop`

- clears semantic UI state
- only stops if the selected document is playing or paused
- otherwise logs that the stop was ignored

## 5. Semantic Search Logic

Semantic ranking is handled by:

- [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt)
- [SpeakerSemanticSearch.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticSearch.kt)
- [SpeakerSemanticIndexer.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticIndexer.kt)

### 5.1 Indexing

`SpeakerSemanticIndexer.indexDocument(...)`:

- splits the document into shared content lines
- builds sliding chunk windows
- trims and joins chunk text
- optionally generates embeddings

### 5.2 Decision Rules

`SpeakerSemanticModule.resolve(...)`:

1. builds semantic query from ASR text
2. ranks indexed chunks
3. finds the first result above `minimumScoreThreshold`
4. maps the best result to a visible line index
5. returns:
   - `AutoPlay` if score >= `autoPlayScoreThreshold`
   - `Candidate` otherwise
   - `NoMatch` if nothing passes the threshold

## 6. LLM Fallback Logic

LLM fallback is also owned by:

- [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt)

Relevant methods:

- `buildLlmRequest(...)`
- `tryLlmFallback(...)`
- `parseLlmResponse(...)`
- `resolveNoMatchOutcome(...)`

Fallback only happens after:

1. deterministic command resolution across utterance variants fails
2. semantic search on content chunks returns `NoMatch`

### 6.1 Request Inputs

The LLM request is now built from:

- aggregated ASR transcript variants collected during one countdown window
- explicit command options:
  - `play_document`
  - `play_line`
  - `pause`
  - `stop`
  - `no_action`
- every visible content line as:
  - `lineIndex`
  - `text`
- configured model name
- expected JSON schema

### 6.2 Response Mapping

The parser currently accepts two schema families:

Legacy candidate schema:

- `match`
- `candidate`
- `autoplay`
- `play`
- `ambiguous`
- `no_match`

New action schema:

- `play_document`
- `play_line`
- `pause`
- `stop`
- `no_action`

If the LLM returns legacy `match` / `candidate` / `autoplay` / `play`, the result is mapped back to an existing ranked candidate by matching `lineStart` and `lineEnd`.

If the LLM returns new `action` JSON:

- `play_line` uses `lineIndex` and `confidence`
- `play_document` becomes runtime `Command(Play)`
- `pause` becomes runtime `Command(Pause)`
- `stop` becomes runtime `Command(Stop)`
- `no_action` becomes runtime no-op

Confidence gating currently applies:

- `play_line`
  - below `0.55` => reject
  - `0.55` to `< 0.82` => `Candidate`
  - `>= 0.82` => `AutoPlay`
- command actions (`play_document`, `pause`, `stop`)
  - below `0.80` => reject
  - `>= 0.80` => runtime command

If no candidate can be matched, the result falls back to `NoMatch`.

## 7. LLM Prompt Structure

Prompt construction lives in:

- [SpeakerSemanticPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/SpeakerSemanticPromptBuilder.kt)
- [SpeakerCommandResolutionPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/SpeakerCommandResolutionPromptBuilder.kt)

The builder returns:

- `systemInstruction`
- `userPrompt`
- `expectedResponseSchema`

### 7.1 Current System Instruction

The current system instruction is conceptually:

- resolve a spoken command to one of the provided document candidates
- only choose from the provided candidates
- if none match, return `no_match`

### 7.2 Current User Prompt Shape

The current aggregated command-resolution prompt contains:

- all ASR variants collected during one countdown window
- explicit command options
- every visible line with `lineIndex`

The line block is formatted like:

```text
- lineIndex=3 text=候選內容
```

### 7.3 Current Expected JSON Schema

The aggregated action schema currently asks for:

```json
{
  "action": "play_document | play_line | pause | stop | no_action",
  "lineIndex": 0,
  "confidence": 0.0,
  "reason": "short explanation"
}
```

Note:

- parser still keeps backward compatibility with the older candidate-based JSON shape

## 8. Logger Inventory

### 8.1 Content ASR / Command Logs

From [SpeakerScreen.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/ui/speaker/SpeakerScreen.kt):

- `Speaker content ASR text: ...`
- `Speaker content ASR variants: [...]`
- `Speaker content ASR final skipped because active target is ...`
- `Speaker content ASR final skipped because version=... lastHandled=...`

From [SpeakerContentAsrActions.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentAsrActions.kt):

- `Speaker content command resolve primary=... matchedVariant=... command=... isPlaying=... isPaused=...`
- `Speaker content command matched: Play`
- `Speaker content command matched: Pause`
- `Speaker content command matched: Stop`
- `Speaker content command matched: PlayLine(...)`
- `Speaker content command Pause ignored because ...`
- `Speaker content command Stop ignored because ...`
- `Speaker content command not matched across utterance variants=...; continuing to semantic resolution`
- `Speaker semantic command command=... confidence=... reason=...`

### 8.2 Semantic Logs

From [SpeakerContentAsrActions.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentAsrActions.kt) and [SpeakerSemanticLogging.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticLogging.kt):

- query embedding preview
- top3 ranked results with semantic and hybrid scores
- candidate/autoplay chosen line index
- line range and matched text preview
- command confidence / reason for LLM command decisions

From [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt):

- `Speaker LLM request built variants=... lines=... rankedCandidates=... model=...`
- `Speaker LLM payload parsed action=... decision=... lineIndex=... lineRange=... confidence=... reason=...`

### 8.3 Gemini / OAuth Logs

From [GeminiAccessTokenProvider.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/GeminiAccessTokenProvider.kt):

- token request started
- token request succeeded
- token request failed
- token invalidation started / succeeded / failed

From [GeminiLlmProvider.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/GeminiLlmProvider.kt):

- request start
- token acquired
- HTTP execution start
- response code and body length
- 401 retry
- rate limit
- response parse success
- response parse failure
- transport failure

## 9. Current Weak Spots

These are the main behavior gaps in the current implementation:

- lexical semantic ranking still runs before the LLM even when the utterance may be purely command-shaped
- LLM `no_action` currently collapses into runtime `NoMatch`, so no dedicated user-visible handling exists yet
- confidence thresholds are constants in runtime code, not settings/config driven
- logger coverage is now much better, but there is still no single structured event model for analytics

## 10. Files Most Relevant for Future Changes

If you want to change command behavior, start here:

- [SpeakerCommandResolver.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerCommandResolver.kt)
- [SpeakerContentAsrActions.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentAsrActions.kt)

If you want to change line segmentation, start here:

- [SpeakerContentLines.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerContentLines.kt)

If you want to change semantic/LLM fallback behavior, start here:

- [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt)
- [SpeakerSemanticPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/SpeakerSemanticPromptBuilder.kt)
- [SpeakerCommandResolutionPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/SpeakerCommandResolutionPromptBuilder.kt)
- [GeminiLlmProvider.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/GeminiLlmProvider.kt)

## 11. Recent Bug: LLM Returned `play_line` But Runtime Dropped It

Observed device log pattern:

- Gemini returned HTTP `200`
- parser logged:
  - `action=play_line`
  - `lineIndex=9`
  - `confidence=0.0`
  - empty `reason`
- runtime then rejected the result because `play_line` requires confidence above the minimum threshold

Example symptom:

```text
Speaker LLM payload parsed action=play_line decision= lineIndex=9 lineRange=null-null confidence=0.0 reason=
```

### Root Cause

There were two practical issues:

1. The command-resolution prompt strongly suggested the desired schema, but it was not strict enough about mandatory numeric `confidence`.
2. The parser only trusted native numeric JSON fields, so responses such as:
   - missing `confidence`
   - `"confidence": "0.93"`
   - `"lineIndex": "9"`
   could degrade into a parsed confidence of `0.0` or null-like behavior.

There was also a logging annoyance:

- the same LLM request could be built twice in one no-match flow, so `Speaker LLM request built ...` appeared twice.

### Applied Fix

Prompt-side fix:

- [SpeakerCommandResolutionPromptBuilder.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/prompt/SpeakerCommandResolutionPromptBuilder.kt)
  now explicitly requires:
  - all JSON fields to be present
  - `confidence` to be numeric and between `0.0` and `1.0`
  - `reason` to be non-empty
  - `lineIndex` to be integer for `play_line`
  - `lineIndex = null` for non-line actions

Parser-side fix:

- [SpeakerSemanticModule.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/speaker/SpeakerSemanticModule.kt)
  now accepts flexible field encodings:
  - numeric JSON values
  - string-encoded numbers for `confidence`
  - string-encoded integers for `lineIndex`, `lineStart`, `lineEnd`

Flow fix:

- the fallback path now builds the LLM request once and reuses it, removing duplicate
  `Speaker LLM request built ...` logs for the same utterance.

Logging fix:

- [GeminiLlmProvider.kt](/home/hhs/workspace/ezTalkAPP/app/src/main/java/tw/com/johnnyhng/eztalk/asr/llm/GeminiLlmProvider.kt)
  now logs a short `rawTextPreview` on successful parse, so the exact Gemini payload shape is visible in logcat.

### Expected Result After Fix

If Gemini returns any of these:

```json
{"action":"play_line","lineIndex":9,"confidence":0.93,"reason":"closest line"}
```

```json
{"action":"play_line","lineIndex":"9","confidence":"0.93","reason":"closest line"}
```

the runtime should now parse the same intent correctly and pass confidence gating.
