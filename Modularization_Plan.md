# ezTalk 模組化與數據採集模式獨立化計劃

本文件記錄了將 `HomeScreen` 中的數據採集 (Data Collect) 功能獨立化並進行代碼模組化的詳細步驟。

## 🎯 核心目標
1.  **職責分離**: `HomeScreen` (或 `TranslateScreen`) 專注於即時辨識與翻譯；`DataCollectScreen` 專注於語料收集。
2.  **組件重用**: 提取通用的 UI 零件（如候選字列表、按鈕組）與邏輯（ASR 引擎、音訊錄製）。
3.  **流程優化**: 針對數據採集設計專屬的自動化流程（自動存檔、自動下一句）。

---

## 📅 五階段實施計劃

### Phase 1: 基礎組件與核心邏輯提取 (Infrastructure)
*   **目標**: 消除重複代碼，建立共享的 Widget 與 Manager。
*   **工作項目**:
    *   **CandidateList Widget**: 將 `LazyColumn` 顯示候選字的部分提取為獨立組件 `tw.com.johnnyhng.eztalk.asr.widgets.CandidateList`。
    *   **AudioCaptureManager**: 封裝 `AudioRecord` 的初始化、串流讀取與波形數據產生邏輯，減少 Composable 內的 Side Effect。
    *   **RecognitionManager**: 建立一個單例或注入式的 Manager，處理 `SimulateStreamingAsr` 的調用、WAV 轉檔與 JSONL 存檔，並提供 `Flow` 回傳結果。
*   **測試點**: 
    *   `TranslateScreen.kt` 改用新組件後，錄音與候選字選擇功能運作如常。
    *   `Home.kt` 改用新組件後，功能保持正常。

### Phase 2: 導航架構與新頁面骨架 (Navigation & UI Shell)
*   **目標**: 建立應用程式的新導航結構，支持分頁切換。
*   **工作項目**:
    *   在 `NavRoutes` 中新增 `DATA_COLLECT` 路由。
    *   在 `MainActivity` 或主 Scaffold 中實作 `BottomNavigationBar`。
    *   建立 `DataCollectScreen.kt` 初步佈局（包含大標題顯示目標文本）。
*   **執行拆分（4 Steps）**:
    1. 盤點現有導覽入口與畫面註冊點（`NavHost`、route 常數、底欄容器）。
    2. 新增 `DATA_COLLECT` route 並完成 `NavHost` 註冊，先確保可直接導航進空白頁。
    3. 在主 Scaffold 接上 `BottomNavigationBar`，完成「翻譯 / 採集」雙分頁切換。
    4. 建立 `DataCollectScreen.kt` UI Shell（標題、佔位內容、基本狀態顯示），確認不影響現有 `Home/Translate` 行為。
*   **測試點**: 使用者可以透過底欄在「翻譯」與「採集」頁面間切換。

### Phase 3: 數據隊列管理與 ViewModel (State Management)
*   **目標**: 遷移數據採集的核心狀態。
*   **工作項目**:
    *   建立 `DataCollectViewModel`。
    *   遷移 `textQueue`、`QueueState` 的讀取、解析（txt/csv）與持久化保存邏輯。
    *   實作「匯入檔案」功能，並與 `ActivityResultLauncher` 整合。
*   **測試點**: 進入採集頁面，點擊匯入後能正確顯示第一句，並能手動切換「上一句/下一句」。

### Phase 4: 採集流程自動化整合 (Collection Workflow)
*   **目標**: 實作「錄音結束即存檔並跳轉」的專屬體驗。
*   **工作項目**:
    *   整合 `RecognitionManager` 到 `DataCollectScreen`。
    *   **Auto-Flow**: 當辨識完成並生成 `wav` 後，自動執行 `saveJsonl` 並將 `currentIndex + 1`。
    *   自定義 `DataCollectButtonRow`：移除複製按鈕，加入「跳過 (Skip)」與「重錄 (Retry)」按鈕。
*   **測試點**: 在採集頁面錄音，結束後檢查檔案是否自動生成，且 UI 是否自動顯示下一句文本。

### Phase 5: 遺留代碼清理與全局優化 (Cleanup & Sync)
*   **目標**: 徹底移除冗餘，確保各模式間狀態一致。
*   **工作項目**:
    *   刪除 `Home.kt` (或 `TranslateScreen.kt`) 中所有 `isDataCollectMode`、`isSequenceMode` 的判斷與變數。
    *   確保 `userId` 與 `userSettings` 在分頁切換時即時同步（透過 Shared ViewModel 或 DataStore）。
    *   優化 ASR 模型載入：確保切換頁面時不重複初始化引擎。
*   **測試點**: 檢查 `Home.kt` 代碼量，確保其邏輯只專注於單次辨識；全 App 進行回歸測試。

---

## 📝 修改記錄 (Process Logs)

| 階段 | 日期 | 執行動作 | 備註 |
| :--- | :--- | :--- | :--- |
| **Initial** | 2024-05-22 | 建立模組化計劃書 | 確定五階段流程 |
| **Phase 1** | | | *Pending* |
| **Phase 2** | 2026-03-24 | 完成 `DATA_COLLECT` 路由、底欄切換與 `DataCollectScreen` UI Shell | *Completed* |
| **Phase 3** | 2026-03-24 | 啟動 `DataCollectViewModel`，遷移 queue 狀態與 txt/csv 匯入流程 | *In Progress* |
| **Phase 4** | | | *Pending* |
| **Phase 5** | | | *Pending* |
