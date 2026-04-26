# Home.kt TTS Button 與 Remote Candidate / JSONL 邏輯紀錄

本文只描述目前程式的既有行為，方便後續修改時對照；不包含任何邏輯調整。

## 1. TTS 按鈕在 UI 上何時會出現、何時可按

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:936-950`

- 只有 `result.wavFilePath.isNotEmpty()` 時才會顯示該列的操作按鈕。
- TTS 按鈕只在 `!isDataCollectMode && result.mutable` 時顯示。
- 因此在以下情況 TTS 按鈕不會出現：
  - Data collect mode
  - 該筆 transcript 已被鎖定為 `mutable = false`
- 按鈕雖然顯示，但只有在以下條件都成立時才能按：
  - `!isStarted`
  - `currentlyPlaying == null`
  - `!isTtsSpeaking`
  - `!isEditing`

## 2. 按下 TTS 按鈕後實際做了什麼

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:611-697`

`handleTtsClick(index, text)` 的流程如下：

1. 先執行 `MediaController.stop()`，停止目前任何 wav 播放。
2. 產生新的 `utteranceId`。
3. 呼叫 `tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)`。
   - `QUEUE_FLUSH` 代表會清掉先前排隊的 TTS 內容。
4. 取出目前列表中的 `item = resultList[index]`。
5. 決定是否啟用 feedback 邏輯：
   - `useFeedbackLogic = userSettings.enableTtsFeedback && !isDataCollectMode`

## 3. TTS 播放狀態如何被管理

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:245-274`

- `TextToSpeech` 在 `LaunchedEffect(Unit)` 中初始化。
- 語言優先使用 `Locale.TRADITIONAL_CHINESE`，不可用時退回 `Locale.getDefault()`。
- `UtteranceProgressListener` 會更新 `isTtsSpeaking`：
  - `onStart()` -> `true`
  - `onDone()` -> `false`
  - `onError()` -> `false`
- UI 上其他按鈕與錄音流程會用 `isTtsSpeaking` 避免和 TTS 同時進行。

## 4. TTS 按下後的兩條分支

### 4.1 啟用 feedback 邏輯時

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:626-676`
以及 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/Api.kt:100-114`

條件：

- `userSettings.enableTtsFeedback == true`
- `!isDataCollectMode`

流程：

1. 背景執行 `feedbackToBackend(userSettings.backendUrl, item.wavFilePath, userSettings.userId)`。
2. `feedbackToBackend()` 會先讀同名 `.jsonl`。
3. 若 jsonl 內已有 `remote_candidates`：
   - 走 `PUT /api/updates`
4. 若 jsonl 內沒有 `remote_candidates`：
   - 走 `POST /api/transfer`
5. 若 feedback 成功：
   - 重新以 `wavFilePath` 在 `resultList` 找目前那筆資料，避免 index 漂移。
   - 把該筆資料更新成：
     - `modifiedText = text`
     - `checked = true`
     - `mutable = false`
     - `removable = true`
   - 再把最新狀態覆寫回 jsonl。
6. 若 feedback 失敗：
   - 顯示 `feedback_failed` toast

關鍵效果：

- TTS 按鈕除了朗讀，也可能觸發「送後端確認/上傳」。
- 一旦 feedback 成功，該筆 transcript 會被鎖住，之後 TTS 按鈕也會消失，因為 `result.mutable` 變成 `false`。

### 4.2 未啟用 feedback 邏輯時

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:677-695`

流程：

1. 不呼叫後端。
2. 直接把當前 item 更新成：
   - `modifiedText = text`
   - `checked = true`
3. 保留原本的：
   - `mutable`
   - `removable`
   - `remoteCandidates`
4. 背景呼叫 `saveJsonl(...)` 覆寫同名 jsonl。

## 5. 遠端 candidate 不是在按 TTS 時取得

這點很重要。遠端 candidate 的取得不是 `handleTtsClick()` 觸發，而是錄音結束、wav/jsonl 初始落盤後，由背景 queue 處理。

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:205-243`
與 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:520-545`

流程如下：

1. 語音片段完成後，先做本地 ASR，得到 `originalText`。
2. 產生檔名，例如 `${timestamp}.app`。
3. `saveAsWav(...)` 先把 wav 寫到 `context.filesDir/wavs/$userId/`。
4. 若 wav 寫成功，立刻執行一次 `saveJsonl(...)`，先建立初始 jsonl。
5. 若 `recognitionUrl` 不為空且目前不是 data collect mode，才會：
   - `recognitionQueue.trySend(wavPath)`
6. 背景 coroutine 持續從 `recognitionQueue` 取出 `wavPath`。
7. 取出對應的 `Transcript` 後，呼叫 `getRemoteCandidates(...)`。
8. 若有拿到遠端 candidate，回到主執行緒把 `resultList[index].remoteCandidates` 更新成該清單。

結論：

- TTS 按鈕本身不負責抓 remote candidates。
- TTS 只會使用目前 `resultList` 上已存在的 `modifiedText` 與 `remoteCandidates`。

## 6. 初始 jsonl 是怎麼建立的

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt:530-540`
與 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt:85-125`

初始建檔時寫入的欄位：

- `original`
- `modified`
- `checked`
- `mutable`
- `removable`
- `remote_candidates` 只有傳入非 null 時才會寫

在 `Home.kt` 初次儲存時呼叫為：

- `originalText = ASR result.text`
- `modifiedText = if (isDataCollectMode) dataCollectText else result.text`
- `checked = isDataCollectMode`
- `mutable = !isDataCollectMode`
- 沒有傳 `remoteCandidates`
- 沒有傳 `removable`，所以使用預設值 `false`

因此一般 Home 非 data collect 模式下，初始 jsonl 通常長這樣：

```json
{
  "original": "...",
  "modified": "...",
  "checked": false,
  "mutable": true,
  "removable": false
}
```

## 7. getRemoteCandidates() 如何把遠端 candidate 存回 jsonl

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/RecognitionUtils.kt:10-76`

流程如下：

1. 先定位同名 jsonl：
   - `wavFilePath` 同層、同 basename、副檔名換成 `.jsonl`
2. 先讀既有 jsonl。
3. 若 jsonl 已有 `remote_candidates` 且長度大於 0：
   - 直接返回既有內容
   - 不會再打遠端 API
4. 若尚未有 candidates 且 `recognitionUrl` 不空：
   - 呼叫 `postForRecognition(recognitionUrl, wavFilePath, userId)`
5. 從 response 中讀 `sentence_candidates`
6. 重讀一次 jsonl，目的是保留使用者可能已修改過的最新欄位
7. 從最新 jsonl 取出：
   - `original`
   - `modified`
   - `checked`
   - `mutable`
8. 呼叫 `saveJsonl(...)` 把整份 json 重新覆寫，並帶入 `remoteCandidates = sentences`

關鍵點：

- 它不是 append candidate，而是整份 jsonl 重新寫一次。
- 重新寫之前，會先 reread jsonl，避免把使用者剛編輯過的 `modified` 或狀態欄位蓋掉。

## 8. saveJsonl() 的實際寫檔特性

參考 `app/src/main/java/tw/com/johnnyhng/eztalk/asr/utils/WavUtil.kt:85-125`

- 檔案路徑：`context.filesDir/wavs/$userId/$filename.jsonl`
- 雖然註解寫的是 JSONL / append，但實作其實是：
  - 建立一個 `JSONObject`
  - `file.writeText(jsonLine)`
- 也就是每次都整檔覆寫，不是累加多行。
- 檔案內容通常只有單行 JSON，再加結尾換行。

這表示：

- Home 內所有「更新 transcript 狀態」與「寫入 remote_candidates」本質上都是覆寫同一份 metadata 檔。

## 9. 目前 TTS 與 remote candidate / jsonl 之間的關係總結

可以把它理解成兩條彼此交會的流程：

### 流程 A: 錄音完成後的 metadata 建立與 candidate 擴充

1. 本地 ASR 完成
2. 寫 wav
3. 建立初始 jsonl
4. 背景 queue 取 remote candidates
5. remote candidates 覆寫回同一份 jsonl
6. UI 的 `resultList` 同步更新 `remoteCandidates`

### 流程 B: 使用者按 TTS 做確認

1. 停止 wav 播放
2. 朗讀 `modifiedText`
3. 視設定決定是否送 backend feedback
4. 更新 `checked / mutable / removable / modifiedText`
5. 再次覆寫同一份 jsonl

## 10. 目前值得注意的行為特徵

- TTS 按鈕不是 candidate fetch trigger。
- candidate fetch 發生在錄音完成之後，而且只在非 data collect mode 執行。
- `feedbackToBackend()` 是否走 update 或 transfer，取決於 jsonl 是否已經帶有 `remote_candidates`。
- `saveJsonl()` 是覆寫，不是 append，因此任何欄位遺漏都可能在覆寫時消失。
- `getRemoteCandidates()` 有刻意先 reread jsonl，這是在避免和使用者編輯 / TTS 確認流程互相覆蓋。
- TTS feedback 成功後會把該筆 `mutable` 設成 `false`，這會直接改變 Home 列表上該筆資料後續是否還能再按 TTS 或編輯。

## 11. 新增的 Home autoplay queue 行為

這是獨立於「使用者手動按 TTS」之外的另一條確認路徑。

### 11.1 什麼情況會進 autoplay queue

- 需要 `userSettings.autoplay == true`
- 同時需要：
  - `userSettings.enableHomeLlmCorrection == true`
  - `userSettings.enableTtsFeedback == false`
- 只有 LLM correction 的結果才可能進 queue。
- 即使 correction 已經被自動套用，仍然只有 `confidence >= 0.9` 的句子會進 queue。

### 11.2 queue 的去重與更新方式

- queue 以 `wavFilePath` 當 key。
- 同一筆 transcript 若先後收到多次高信心 correction：
  - 不會排出多筆
  - 而是用最新的 `text + confidence` 覆蓋舊的 queue item

### 11.3 queue 何時真的開始播放

Autoplay 不會因為 correction 一回來就立刻播放，而是要等以下條件都成立：

- `!isRecognizingSpeech`
- `countdownProgress <= 0f`
- `currentlyPlaying == null`
- `!isAnyTtsSpeaking`
- `!isAsrModelLoading`

這表示：

- 只要 Home 還在倒數 / 語音辨識視窗中，就不會 autoplay。
- autoplay 不要求當下 `isStarted == true`。

### 11.4 autoplay 與錄音狀態的互動

- 若 autoplay 開始前 Home 原本正在錄音：
  - 先停止錄音
  - 播放 TTS
  - 播放完成後再恢復錄音
- 若 autoplay 開始前原本沒有在錄音：
  - 就只播 TTS
  - 不會額外啟動錄音

這是「restore the previous recording state」的邏輯，而不是單純要求 autoplay 只能在錄音中發生。

### 11.5 autoplay 完成後怎麼寫回 transcript / jsonl

- autoplay 播完後才會做確認，不是在開播前就先改狀態。
- 確認後會把該筆 transcript 更新成：
  - `checked = true`
- 但因為這條路徑明確要求 `enableTtsFeedback == false`，所以：
  - 不會呼叫 backend feedback
  - 不會把 `mutable` 改成 `false`
  - 不會把 `removable` 改成 `true`
- 最後仍然會把最新 transcript 狀態覆寫回同一份 jsonl。

### 11.6 與手動 TTS 的差異

- 手動 TTS 是使用者直接觸發。
- autoplay queue 是 LLM correction 背景流程觸發。
- 手動 TTS 可以在 `enableTtsFeedback` 開啟時走 feedback / lock transcript。
- autoplay queue 明確只在 `enableTtsFeedback == false` 時運作，因此它永遠不會走 feedback 分支。
