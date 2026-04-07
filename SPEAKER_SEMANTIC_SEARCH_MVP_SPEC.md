# Speaker Semantic Search MVP Spec

## Goal

Upgrade `Speaker` mode from keyword-only command matching to local semantic retrieval.

The user should be able to speak an approximate idea instead of the exact sentence and still trigger the correct content playback.

Examples:
- user says: `學校的回憶`
- app matches a line or chunk similar to:
  - `想起去年秋天的校園生活`

## Product Goals

### 1. Semantic Search
- The user does not need to read the exact stored sentence.
- The app should match semantically similar content.

### 2. Local-Only Execution
- No cloud vector database.
- No server-side retrieval.
- All indexing and search run on-device.

### 3. Low Latency
- Search should feel immediate after local ASR final text is produced.
- Target should be playable without noticeable delay on a normal modern phone.

### 4. High Tolerance For ASR Errors
- The system should still work if ASR output is imperfect.
- It should not rely only on exact keyword overlap.

## Scope

## In Scope
- Semantic retrieval inside `Speaker` mode
- Local embedding model
- Local similarity scoring
- Matching current file content first
- Jumping to the best matching line / chunk
- Reusing current TTS playback, highlight, and auto-scroll behavior

## Out Of Scope
- Gemini
- Cloud vector DB
- Remote semantic search
- Full document-to-document semantic browsing UI
- Multi-language retrieval tuning beyond current app language setup

## Retrieval Strategy

Use a two-layer local retrieval strategy.

### Layer 1: Rule-Based Command Routing

This remains the first gate.

Handle deterministic commands such as:
- `播放`
- `暫停`
- `停止`
- `上一個`
- `下一個`
- `第 N 行`

If a valid command is recognized, execute it directly and do not run semantic search.

### Layer 2: Semantic Retrieval

If the ASR final text is not a recognized deterministic command:
- embed the ASR query locally
- compare it against indexed content
- pick the best matching result
- jump to and play that content

## MVP Search Scope

### Phase 1 Search Scope
- Search only inside the currently selected document

Why:
- smallest implementation surface
- easiest to validate quality
- no file-switching ambiguity
- easiest to reuse current line highlight and TTS flow

### Later Phase
- Expand to cross-document search
- allow retrieval across:
  - current expanded folder
  - all expanded folders
  - all indexed speaker files

## Indexing Unit

Do not rely on only one granularity.

### Keep Two Granularities

1. `line`
- used for final playback location

2. `chunk`
- used for semantic retrieval
- each chunk may contain 2 to 4 consecutive lines

Why:
- a single short line may be too sparse for embedding quality
- short ASR text may align better with a small chunk than a single line
- playback still needs a concrete line index

## Recommended Local Model

### MediaPipe Text Embedder
- component: Google MediaPipe Tasks Text
- model candidate:
  - `universal_sentence_encoder.tflite`

This is the baseline recommendation for local semantic retrieval.

## Data Model

Add local search-specific models.

### `SpeakerIndexedChunk`
- `documentId: String`
- `lineStart: Int`
- `lineEnd: Int`
- `text: String`
- `embedding: FloatArray`

### `SpeakerSemanticQuery`
- `text: String`
- `embedding: FloatArray`

### `SpeakerSearchResult`
- `documentId: String`
- `lineStart: Int`
- `lineEnd: Int`
- `matchedText: String`
- `semanticScore: Float`
- `lexicalScore: Float`
- `finalScore: Float`

## Scoring Strategy

Do not use only cosine similarity.

Use hybrid scoring:

`finalScore = semanticScore * 0.7 + lexicalScore * 0.3`

### Semantic Score
- cosine similarity between:
  - query embedding
  - indexed chunk embedding

### Lexical Score
Local lightweight overlap score based on:
- token overlap
- character overlap
- simple n-gram overlap

Why:
- improves tolerance to ASR spelling errors
- helps short queries
- reduces bad matches from embedding alone

## Search Flow

### At Document Load Time
1. split document into lines
2. build chunk windows
3. embed each chunk
4. cache in memory

### At ASR Final Text Time
1. run rule-based command resolver
2. if command found:
   - execute command
   - stop
3. otherwise:
   - embed ASR final text
   - score against all indexed chunks in current document
   - take highest scoring result
4. map winning chunk to target line
5. auto-scroll to target line
6. highlight target line
7. start playback from that line or chunk

## Playback Integration

MVP should reuse existing `Speaker` playback behavior.

### Required Integration
- selected document remains current document
- jump to target line
- highlight target line
- auto-scroll target into view
- stop current TTS before new playback starts

### MVP Playback Decision
When semantic match hits a chunk:
- start playback from `lineStart`

This keeps implementation simple and aligns with current line-based UI.

## Caching Strategy

### MVP
- in-memory only

### Rebuild Conditions
Re-index the current document when:
- selected file changes
- file content is edited and saved
- file is re-imported or replaced

### Not Needed Yet
- disk-persisted embedding cache
- background pre-indexing for all files

## Acceptance Criteria

### Core
- ASR final text that is not a command can trigger semantic search
- app finds the most relevant chunk in the selected document
- app scrolls to the matched area
- app highlights the matched line
- app starts playback from the matched location

### Quality
- approximate semantic matches work for simple paraphrases
- exact sentence match still works
- ASR with minor wording errors still retrieves a reasonable target

### Safety
- if score is too low:
  - do nothing
  - or show a lightweight no-match message
- current TTS stops before new semantic playback begins

## Failure Behavior

If no result is strong enough:
- do not jump randomly
- do not play unrelated text
- show a small toast or silent no-op

Suggested MVP threshold:
- configurable local threshold
- start with a conservative value and tune with real usage

## Implementation Plan

### Step 1
Create local semantic search interfaces:
- `SpeakerSemanticIndexer.kt`
- `SpeakerSemanticSearch.kt`
- `SpeakerSearchModels.kt`

### Step 2
Index current document only

### Step 3
Connect lower-pane ASR final text to:
- command resolver first
- semantic search second

### Step 4
Map winning chunk to:
- line highlight
- auto-scroll
- playback start

### Step 5
Tune score threshold and chunk size

## Suggested File Additions

- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerSemanticIndexer.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerSemanticSearch.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/SpeakerSearchModels.kt`

## Deferred Follow-Up

- cross-document semantic retrieval
- semantic retrieval from explorer ASR
- candidate list UI for ambiguous matches
- Gemini fallback for low-confidence retrieval
