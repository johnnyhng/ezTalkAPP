# ezTalk Session Restore

更新日期：2026-03-25

## Current Status
- Modularization 進度：
  - Phase 2: Completed（含 cleanup）
  - Phase 3: In Progress
  - Phase 4: In Progress（Auto-Flow / Skip / Retry 已起步）
- DataCollectScreen 已可獨立運作，不再只是一個骨架頁面。

## Completed In This Session
- DataCollectScreen：
  - 已接入錄音 Start/Stop、波形顯示、即時辨識列表。
  - 已接入播放/停止、刪除檔案按鈕。
  - 已接入辨識中的 circular countdown。
  - `text` 為空時禁用 `Start`。
  - 已移除頁面上的 Data Collect 標題文字。
  - 已接入 Auto-Flow：sequence mode 下錄音完成會自動存檔並切到下一句。
  - 已加入採集專用 `Skip/Retry` 按鈕列，取代通用 copy/clear 式流程。
- Data collect queue（Phase 3）：
  - `DataCollectViewModel` 管理 txt/csv 匯入、sequence mode、前後句切換、持久化。
  - 空白尾項按 `Next` 會 clear text 並關閉 sequence mode。
- Recording stability：
  - VAD 操作改為 thread-safe 包裝，避免 `Vad.pop()` 競態 crash。
  - 修正 stop 後再次 start 無反應問題。
  - 修正 data collect `modifiedText` 停留舊句的同步問題（改為動態同步）。
- 文件：
  - `Recording_Crash_Report.md` 已更新為 Mitigated。
  - `Modularization_Plan.md` 已補齊 2026-03-25 過程紀錄。

## Key Files
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/DataCollectScreen.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/DataCollectViewModel.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/managers/RecognitionManager.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/SimulateStreamingAsr.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/Home.kt`
- `app/src/main/java/tw/com/johnnyhng/eztalk/asr/screens/TranslateScreen.kt`
- `Modularization_Plan.md`
- `Recording_Crash_Report.md`

## Next Suggested Steps
1. 完成 Phase 4 收尾：補齊 `Retry` / `Skip` 的使用者提示與邊界情況驗證。
2. 開始 Phase 5：移除 Home 內遺留的 data collect mode 分支，避免雙軌維護。
3. 檢查 shared recognition/model lifecycle，避免 Translate/DataCollect 來回切頁時重複初始化。
