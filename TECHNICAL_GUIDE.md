# ezTalkAPP 技術開發指南 (Technical Guide)

本文件詳細說明 ezTalkAPP 的核心架構、錄音模式以及可配置參數對系統行為的影響。

---

## 1. 錄音與處理模式 (Recording Modes)

ezTalkAPP 提供多種專門的錄音情境，每種模式對應不同的 UI 邏輯與 ASR 後處理流程：

### 1.1 主畫面模式 (`HOME`) 與 翻譯模式 (`TRANSLATE`)
這兩種模式均建立在 Real-time ASR 基礎上，並支援 **Backend Remote Recognition**、**Utterance Variants**、可選的 **LLM Correction**，但觸發機制不同：

*   **主畫面模式 (`HOME`)：**
    *   **倒數機制 (Countdown Mechanism)：** 當 ASR 偵測到一段語音結束後，UI 會自動顯示倒數進度條。
    *   **自動觸發：** 倒數結束後，系統會自動將該段音訊存為 `.wav` 並上傳至 **Backend (Recognition URL)** 進行高精度識別。
    *   **背景 LLM Correction：** 若設定啟用，final transcript 會先以本地 `utteranceVariants` 進行背景修正；遠端候選回來並合併成新的 `utteranceVariants` 後，會再觸發一次修正。
    *   **背景英文翻譯：** 若設定啟用，每一行會在中文下方顯示對應英文，並支援英文 TTS。
*   **翻譯模式 (`TRANSLATE`)：**
    *   **手動觸發：** 系統會持續進行 Local ASR 轉寫，直到使用者**手動點擊停止錄音**。
    *   **後端識別：** 停止錄音後，系統才會將整段錄音回傳至 Backend 獲取更精確的 Remote Candidates。
    *   **候選更新後修正：** 當 local / remote candidates refresh 並造成 `utteranceVariants` 更新時，若使用者尚未手動修改文字且設定啟用，會用新的 variants 重新進行 LLM correction。
*   **共通點：** 兩者都具備 Local ASR 的即時性（Partial Result）、Backend 的高精確度，以及以 `utteranceVariants` 作為 LLM correction 輸入的語意校正能力。

### 1.2 資料收集模式 (`DATA_COLLECT`)
*   **行為：** 除了 ASR 識別外，系統會特別強化音訊的保存。
*   **流向：** 錄音結束後，會自動將該段音訊存為 `.wav`，並搭配識別出的 `JSONL` 標籤。
*   **用途：** 用於收集特定語境下的語料，作為後續 **Fine-tuning SenseVoice** 的訓練素材。

### 1.3 播放控制模式 (`SPEAKER`)
*   **行為：** 進入 **Speaker Mode** 後，ASR 不僅用於轉寫，更作為**語音指令發射器**。
*   **功能：** 透過語音控制文件的播放、暫停、跳轉至特定行。
*   **變體收集機制 (Utterance Aggregation)：** 
    *   由於 Local ASR 在嘈雜環境或特定口音下準確度約僅七成，系統會在倒數視窗期間，利用 `HashSet` (或 `LinkedHashSet`) 記錄所有產出的**唯一辨識結果 (Unique Variants)**。
    *   這種「多版本並存」的策略能有效彌補單一辨識結果錯誤的問題。
*   **邏輯：** 當倒數結束，系統會將這組「唯一辨識結果集合」連同「文件行內容」發送至 **LLM (Gemini)** 進行語義判定。

### 1.4 實驗輔助輸入模式 (`EXPERIMENT`)
*   **行為：** 整合 Google Project VOICE 技術，提供**排除式預測 (Exclusionary Prediction)** 的輔助輸入工作流。
*   **互動模型：** 採用「連續點選」邏輯。
    *   **排除冗餘：** LLM 的 Prompt 嚴禁包含輸入框已有的漢字前綴，僅回傳後續補全部分（Suffix）。
    *   **追加模式 (Append-only)：** 點選建議後，系統會自動剔除尾端注音符號並追加補全文字，將「打字」轉化為一系列的「意圖選擇」。
*   **視覺設計：** 符合 Fitts's Law，使用高對比深色背景、加大觸控面積（高度 64dp+）與加大間距（12dp+），適配橫向螢幕操作。

---

## 2. Speaker 模式下的語音控制與 Gemini 整合

在 Speaker 模式中，語音控制是透過「語義比對」與「LLM 推論」雙軌運行的。

### 2.1 傳送給 Gemini 的資料結構
當本地匹配失敗或不夠精確時，系統會組裝以下資料發送給 Gemini：
*   **ASR 變體集合 (Utterance Variants):** 該次發言中所有被捕捉到的不同辨識版本（去重後）。
*   **可用指令 (Command Options):** `play_document`, `play_line`, `pause`, `stop`, `no_action`。
*   **文件上下文 (Source Lines):** 當前文件的所有行內容及其索引。

### 2.2 LLM 決策機制
Gemini 會根據上下文回傳結構化 JSON，系統依據 **Confidence (信心分數)** 執行不同行為：
1.  **直接執行 (Play/Pause/Stop)：** 信心分數高（例如 > 0.9）且指令明確時。
2.  **高亮候選 (Highlight/Candidate)：** 語義匹配但信心分數中等，系統會先高亮該行而不立即播放。
3.  **不採取行動 (No Action)：** 語義不明確或判定為雜訊時，回傳 `no_action`。

### 2.3 運作範例
**使用者語音：** *"幫我從第五行開始唸"* (Local ASR 可能產生多個錯誤版本)

**Gemini 接收到的內容 (簡化版)：**
```text
System: You resolve spoken speaker-control utterance into exactly one action...
User:
  ASR Unique Variants: ["幫我從第五行", "幫我從地獄行", "幫我從第5行開始唸"]
  Available Lines: 
    - lineIndex=4 text="這是第四行..."
    - lineIndex=5 text="這是第五行..."
    - ...
```

**Gemini 回傳的 JSON：**
```json
{
  "action": "play_line",
  "lineIndex": 5,
  "confidence": 0.95,
  "reason": "Corrected '地獄行' to '第五行' based on variant aggregation and line context."
}
```

**系統行為：** 即使單一 ASR 結果有誤，LLM 也能透過變體集合判斷出正確意圖，並指示 `SpeakerPlaybackController` 跳轉至第五行。

---

## 3. Experiment 模式下的排除式預測與 Firestore 整合

Experiment 分頁實現了一套針對繁體中文優化的 AI 輔助輸入方案。

### 3.1 排除式預測 (Exclusionary Prediction) 邏輯

為了將選擇成本降至最低，系統在 Prompt 層級實施了嚴格的排除規則：
*   **指令：** `嚴禁在輸出中包含「目前輸入」已有的任何漢字前綴。`
*   **字詞建議：** 側重於「注音轉漢字」與「下一個詞」的預測。
*   **語句建議：** 根據意圖生成後續補全，並優先使用台灣口語助詞（啦、喔、呢、吧）。
*   **優點：** UI 不顯示重複文字，點選即追加，實現流暢的「意圖流」輸入。

### 3.2 Firestore 情境動態注入 (Scenario Injection)

系統支援從雲端動態調整預測權重：
*   **資料來源：** Firestore `experiment_contexts` 集合。
*   **欄位：** `keywords` (關鍵字清單), `customInstruction` (情境專屬指令)。
*   **運作：** 當使用者選取「醫療」或「工程」情境時，對應的術語會被注入 Prompt，使 Gemini 生成更具領域專業性的建議。

---

## 4. Home / Translate 的 LLM Correction 與 Utterance Variants

Home 與 Translate 的 LLM correction 不是直接信任單一 ASR 結果，而是以同一段語音中累積出的 `utteranceVariants` 作為主要輸入。這個設計讓模型能在多個可能辨識結果之間推理，降低單一 ASR 誤字造成的錯誤。

### 3.1 `utteranceVariants` 來源

*   **Real-time Local ASR variants：** 錄音期間多次 partial recognition 的不同結果會被去重後保存。
*   **Final Local ASR result：** final recognition 的結果也會加入 variants。
*   **Backend Remote Candidates：** 若 Advanced Settings 的 **Add backend candidates to utterance variants** 啟用，remote `sentence_candidates` 會合併進 `utteranceVariants`。
*   **去重策略：** 合併時會 trim、移除空字串，並保留第一次出現的順序。

### 3.2 Home LLM Correction 流程

1.  `RecognitionManager` 偵測到語音結束，產生 final transcript。
2.  `.wav` 與 `.jsonl` 先落地，`utterance_variants` 一併寫入 JSONL。
3.  Home 收到 final transcript 後，若 `enableHomeLlmCorrection` 啟用，會在背景以目前 variants 呼叫 LLM correction。
4.  UI 不等待 correction；倒數結束後會立即進入下一輪錄音與 real-time local ASR，避免阻塞 UX。
5.  Remote candidates 回來後，若設定允許，會合併進 `utteranceVariants`，更新 JSONL，並再 queue 一次 LLM correction。
6.  correction 回來時只在 transcript 仍可修改、且 `modifiedText` 尚未被使用者或其他流程改動時套用，避免 race condition 覆蓋手動編輯。

### 3.3 Translate LLM Correction 流程

1.  Translate 停止錄音後產生單一 transcript。
2.  若 `enableTranslateLlmCorrection` 啟用，先使用當下 local variants 進行一次 correction。
3.  之後 candidate loader 會補 local re-recognition 與 remote candidates。
4.  若 candidate refresh 造成 `utteranceVariants` 更新，且使用者尚未手動改過文字，會使用新的 variants 再跑一次 correction。
5.  更新後會寫回 JSONL，讓後續 feedback、reload、logger 都看到一致狀態。

### 3.4 Prompt 與輸出約束

`TranscriptCorrectionPromptBuilder` 要求 LLM 回傳 JSON，核心欄位為：

```json
{
  "corrected_text": "修正後文字",
  "confidence": 0.0,
  "reasoning": "簡短原因"
}
```

系統只會自動套用 `confidence >= 0.85` 且 `corrected_text` 非空的結果。Prompt 也要求若輸入含簡體中文，輸出應轉為繁體中文。

### 3.5 English Translation

Home 支援可選的中文轉英文背景翻譯：

*   設定項目為 **Home English translation**。
*   翻譯來源是目前的 `modifiedText`。
*   若 LLM correction、手動編輯、TTS confirmation 或 dialog confirmation 改變 `modifiedText`，既有英文會清空並重新翻譯。
*   英文翻譯存入 JSONL 的 `english_translation` 欄位，並支援獨立英文 TTS。

---

## 4. 核心 ASR 實作：Fine-tuned SenseVoice ONNX

本專案利用 `sherpa-onnx` 運作離線 ASR 引擎。

*   **模型類型 (asrModelType = 43):** 專為 **SenseVoice** 結構設計的推論配置，支援多語言與情感/語氣標記。
*   **VAD (Silero VAD):** 負責切分串流。這是一個「生產者-消費者」模型，VAD 負責生產語音段落，ASR 負責消耗並轉寫。

---

## 5. 可配置參數與行為影響 (Configuration & Behaviors)

你可以透過修改代碼或 `UserSettings` 來調整以下行為：

| 配置項目 | 位置 | 預設值/類型 | 行為影響 |
| :--- | :--- | :--- | :--- |
| **asrModelType** | `SimulateStreamingAsr.kt` | `43` (SenseVoice) | 決定 ASR 推論引擎。若改為 `16` 或其他值，需確保對應的模型檔（Encoder/Decoder）結構相符。 |
| **sampleRateInHz** | `RecognitionManager.kt` | `16000` | 採樣率。SenseVoice 強烈建議維持 16k，變動會導致識別完全失效或語速異常。 |
| **preferredAudioInputDeviceId** | `UserSettings` | `null` (Auto) | 影響錄音源。設定後可強迫系統從藍牙耳機或特定外接麥克風錄音，避開手機內建麥克風。 |
| **min_silence_duration** | `VadConfig` | 0.5s (預設) | **影響延遲感**。數值越小，語音切斷越靈敏（反應快但易斷句）；數值越大，斷句越完整但使用者會感到延遲。 |
| **Audio Mode** | `AudioIOManager.kt` | `MODE_IN_COMMUNICATION` | 影響系統音訊路由。設為通訊模式可啟動藍牙 SCO，但會降低音訊頻寬。若改為 `MODE_NORMAL`，藍牙耳機可能無法正確錄音。 |
| **numThreads** | `OfflineRecognizerConfig` | `2` | 影響推論速度。在低階手機上可增加，但過高會導致系統 UI 掉幀或發燙。 |
| **enableHomeLlmCorrection** | `UserSettings` | `false` | 啟用 Home 背景 LLM correction。第一次用 local variants，remote candidates 合併成新 variants 後會再修正一次。 |
| **enableTranslateLlmCorrection** | `UserSettings` | `false` | 啟用 Translate LLM correction。候選更新後若 variants 改變且文字未被手動改動，會再次修正。 |
| **includeRemoteCandidatesInUtteranceVariants** | `UserSettings` | `true` | 控制 backend remote candidates 是否合併進 `utteranceVariants`，影響 LLM correction 的候選輸入。 |
| **enableHomeEnglishTranslation** | `UserSettings` | `false` | 在 Home 每行下方顯示英文翻譯，並支援英文 TTS。 |
| **geminiModel** | `UserSettings` | 依設定 | LLM correction / English translation / Speaker semantic parse / **Experiment suggestions** 使用的 Gemini model。 |

---

## 6. 音訊路由邏輯 (Audio Routing)

`AudioIOManager` 負責處理 Android 極其複雜的錄音路由：
*   **藍牙支援：** 當偵測到藍牙耳機時，會自動切換至 SCO 通道。
*   **Debounce 釋放：** 結束錄音時有 1000ms 的延遲釋放，避免在連續對話的間隙中，系統音訊模式反覆切換造成的硬體「喀噠」聲。

---

## 7. LLM 整合與身份驗證

*   **OAuth 2.0:** 專案不儲存 API Key，而是使用使用者的 Google Access Token。
*   **Scope:** 需要 `https://www.googleapis.com/auth/generative-language` 權限。
*   **LLM Provider:** `TranscriptCorrectionProviderFactory` 依設定建立 provider；目前 cloud LLM 使用 Gemini OAuth。
*   **行為：** Speaker semantic parse、Home / Translate LLM correction、Home English translation 都會組裝 Prompt 並發送 request。
*   **Logger:** LLM correction request log 會輸出 model、variant count 與 variants 內容，方便確認 remote candidates 是否已納入修正輸入。

---

## 8. 模型更新機制

*   **RemoteModelRepository:** 支援動態更新 ASR 模型。
*   **檔案結構：** 下載的解壓縮目錄必須包含 `model.onnx` 與 `tokens.txt`，否則 `initOfflineRecognizer` 會崩潰。
