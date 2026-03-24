# ezTalk 錄音閃退現象記錄

最後更新日期：2026-03-24 11:46
狀態：Open（待定位 root cause）

## 1) 問題摘要
- 現象：使用者在 Home/翻譯流程啟動錄音時，App 出現閃退。
- 影響：無法穩定完成錄音與後續辨識流程。
- 嚴重度：High（核心功能中斷）。

## 2) 目前已知行為
- 問題名稱：錄音閃退（Recording Crash）。
- 觸發點：點擊「開始錄音」後或錄音流程中。
- 發生頻率：待補（偶發 / 必現）。
- 受影響頁面：`Home.kt`（Phase 1 重構後需持續觀察）。

## 3) 重現步驟（待確認）
1. 開啟 App，進入 Home/翻譯頁。
2. 確認已授權麥克風權限。
3. 點擊「Start」開始錄音。
4. 觀察是否於錄音初始化或錄音中發生閃退。

## 4) 預期 vs 實際
- 預期行為：可正常開始錄音、更新波形、產生辨識文字，不應閃退。
- 實際行為：流程中斷並閃退回系統。

## 5) 待補關鍵證據
- `logcat` 崩潰堆疊（Exception type / message / first app frame）。
- 裝置資訊（機型、Android 版本、ABI）。
- 發生時設定值（`inlineEdit`、`saveVadSegmentsOnly`、模型名稱）。
- 是否與資料採集模式（`isDataCollectMode`）相關。

## 6) 已收集崩潰證據（2026-03-24 11:46）
- Fatal signal：`SIGABRT (signal 6)`，執行緒：`DefaultDispatch`。
- Native frame：`libsherpa-onnx-jni.so -> Java_com_k2fsa_sherpa_onnx_Vad_pop+60`。
- App frame：`RecognitionManager.processAudio`（呼叫鍊對應 `Vad.pop()`）。
- 代表崩潰發生在 VAD 佇列操作階段，而非 UI 層事件處理。

關鍵 log（節錄）：
- `Fatal signal 6 (SIGABRT) ... tid ... (DefaultDispatch)`
- `... libsherpa-onnx-jni.so ... Java_com_k2fsa_sherpa_onnx_Vad_pop+60`
- `... RecognitionManager.processAudio+3908`

## 7) 初步排查方向
- 錄音權限與 `AudioRecord` 初始化參數是否一致。
- 錄音執行緒生命週期（start/stop race condition）。
- 模型初始化與錄音啟動是否有競態（ASR init 尚未完成即啟錄）。
- 重構後狀態切換是否導致空物件或非法狀態。

### 7.1 目前最高優先懷疑
- `RecognitionManager` 的 VAD 操作存在競態風險：
  - 同一全域 `SimulateStreamingAsr.vad` 在多路徑被操作（`Home` 的 `RecognitionManager`、`TranslateScreen` 仍有直接操作）。
  - `while (!vad.empty()) { vad.front(); vad.pop(); }` 在非 thread-safe 情境下可能於 `empty` 與 `pop` 間狀態改變，導致 JNI `abort`。
- `stop()` 後可立即 `start()`，舊的 `recognitionJob` 尚未完全退出時，新舊流程可能重疊使用同一 VAD 實例。

## 8) 追蹤紀錄
- 2026-03-24：建立本文件，先記錄現象與排查欄位。
- 2026-03-24 11:46：補充實際崩潰堆疊，確認崩潰位於 `Vad.pop()` 原生層呼叫。
