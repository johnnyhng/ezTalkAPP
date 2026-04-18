# Audio Routing Research Report (Hardware Verification Phase)

## 🎯 目標
實作「非對稱音訊路由」：藍牙耳機麥克風輸入 (Bluetooth SCO In) + 手機底座喇叭輸出 (Phone Speaker Out)。

## 🧪 測試設備
- **Device**: Google Pixel 10 Pro XL
- **OS**: Android 14+
- **Peripheral**: Pixel Buds A-Series (Bluetooth Headset)

## 🔍 核心發現：Android 底層路由競爭 (Resource Contention)
在 Pixel 設備上，當同時啟用「藍牙 SCO 錄音」與「強制喇叭播放」時，系統底層的 `AudioPolicyService` 會發生劇烈衝突。即便 App 強制指定路由，系統仍會不斷嘗試將音訊拉回「對稱路徑」（即輸入與輸出都在同一個藍牙設備上）。

### 1. 失敗的嘗試與現象記錄
| 方案 | 實作手段 | 結果 | 失敗原因分析 |
| :--- | :--- | :--- | :--- |
| **標準路由指定** | `setCommunicationDevice` + `MODE_IN_COMMUNICATION` | 🔴 失敗 | 系統在播放瞬間會自動切換回藍牙 A2DP。 |
| **音訊屬性優化** | 將 TTS 改為 `NAVIGATION_GUIDANCE` | 🟡 部分成功 | 音量顯著提升，但路由依然會在耳機/喇叭間跳動。 |
| **引用計數管理** | 追蹤錄音與播放 Session 並延遲釋放模式 | 🔴 失敗 | 片段切換時的微小間隙足以讓系統判定「通訊結束」而切換路由。 |
| **物理性互斥 (Lock)** | 播放時 `AudioRecord.stop()` 並使用 Semaphore 鎖定硬體 | 🔴 失敗 | 即便錄音停止，只要 SCO 連結未斷開，系統仍優先走藍牙路徑。 |
| **暴力藍牙開關** | 播放前 `BluetoothAdapter.disable()` | 🔴 已棄用 | 雖然有效，但會導致使用者連線中斷且 Android 13+ 有權限彈窗限制。 |

### 2. 技術瓶頸：為什麼「鎖不住」路由？
1.  **SCO 與 Speaker 的互斥性**：在 `MODE_IN_COMMUNICATION` 下，Android 傾向於將雙向音訊綁定。當我們強行將 Output 分離到 Speaker 時，底層硬體抽象層 (HAL) 的時序競爭會導致路由抖動（Bouncing）。
2.  **Google TTS 的黑箱行為**：Google TTS 作為獨立 Service，其內部的 `AudioTrack` 建立時機與 App 的路由指令存在競爭關係（Race Condition）。
3.  **Pixel 專有策略**：Pixel 系列對藍牙音訊有更嚴格的自動路由保護，這使得「非對稱路由」在錄音硬體活動時極度不穩定。

## 📋 目前現況 (Current Status)
- **已回退程式碼**：由於無法達成 100% 穩定的非對稱路由，且強行關閉藍牙會造成不良使用者體驗，相關暴力邏輯已回退。
- **穩定路徑**：
    - **對稱模式**：藍牙進、藍牙出（最穩定）。
    - **內建模式**：內建 Mic 進、喇叭出（最穩定）。
- **待解決難題**：如何在不關閉藍牙硬體的前提下，徹底禁止 `AudioPolicyService` 在 `MODE_IN_COMMUNICATION` 期間接管路由。

## 💡 未來研究方向
- 探討是否能透過 `AudioDeviceCallback` 在系統切換瞬間立刻「搶奪」回來（但可能造成爆音）。
- 尋找是否有不進入 `MODE_IN_COMMUNICATION` 也能驅動藍牙麥克風的 NDK 底層方法（OpenSL ES 或 AAudio）。
- 測試不同廠牌 (Samsung, Sony) 的路由策略是否有同樣的爭搶行為。
