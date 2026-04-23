# Gemini API 效能優化與實作筆記

本文件記錄了 ezTalk APP 在整合 Gemini API（特別是 Project VOICE Experiment 分頁）過程中的技術挑戰、效能瓶頸及其解決方案。

---

## 1. 輸出格式與延遲 (Latency vs. Format)

### 問題：JSON 強制約束導致巨大延遲
*   **現象**：在 REST 請求中設定 `response_mime_type: "application/json"` 時，Gemini 2.5 Flash 的回應延遲平均高達 **7.5 秒**。
*   **原因**：模型為了確保輸出符合嚴格的 JSON 語法結構，會執行額外的內部校驗與邏輯規劃，這在 Flash 模型上產生了顯著的運算開銷。
*   **解決方案**：
    *   將 Prompt 改為請求 **「純文字」** 輸出，並要求以 **「逗號分隔」**。
    *   移除 `responseMimeType` 的強制要求。
    *   **結果**：延遲大幅下降至 **1.5 ~ 2.5 秒**。

---

## 2. Token 額度與「隱形思考」 (Hidden Thoughts)

### 問題：maxOutputTokens 過低導致輸出截斷
*   **現象**：設定 `maxOutputTokens` 為 30 或 60 時，頻繁出現 `finishReason: MAX_TOKENS`，且候選詞數量遠低於要求。
*   **發現**：Gemini 2.5 Flash 在執行複雜指令（如「轉換注音」或「排除輸入前綴」）時，即使未開啟 Thinking Mode，仍會產生內部的 **Thoughts Tokens**（從 Log 觀察約佔 50-80 Tokens）。
*   **解決方案**：
    *   將 `maxOutputTokens` 放寬至 **1024**。
    *   配合使用 **`stopSequences: ["\n"]`**。
    *   **結果**：既確保了模型有足夠空間完成內部思考並輸出完整結果，又不會因為調高上限而浪費生成時間。

---

## 3. 併發請求與狀態管理 (Concurrent Requests)

### 問題：多重建議請求導致 UI 狀態抖動或覆寫
*   **現象**：同時發起「字詞」與「語句」建議請求時，由於回應抵達時間不一，較晚的回應可能會覆蓋掉較早抵達但仍有效的 UI 狀態（Race Condition）。
*   **解決方案**：
    *   在 `ExperimentViewModel` 採用 **`async/await` 匯合模式**。
    *   發起併發請求後，使用 `await()` 等待所有任務完成，最後執行一次性的 `_uiState.update`。
    *   **結果**：UI 更新變得同步且平滑，消除了閃爍感。

---

## 4. OAuth 認證效能 (OAuth Performance)

### 觀察：系統級快取的冷熱路徑差異
*   **現象**：`GoogleAuthUtil.getToken()` 的耗時在連續呼叫時約 **6ms**，但在間隔較長後呼叫則會升至 **50ms** 左右。
*   **分析**：
    *   **6ms (Hot Path)**：GMS (Google Play Services) 進程已喚醒且 Token 位於記憶體快取。
    *   **50ms (Cold Path)**：涉及 IPC 跨進程喚醒開銷或磁碟快取讀取。
*   **結論**：對於當前需求，50ms 的延遲仍屬極速（網路請求通常 > 200ms），目前無需在 App 層級實作額外的二次快取。

---

## 5. 解析器鲁棒性 (Parser Robustness)

### 問題：模型輸出格式不穩定
*   **現象**：Gemini 有時會回傳純陣列 `[...]`，有時會回傳帶鍵物件 `{"candidates": [...]}`，有時則會在陣列中放入物件 `[{"text": "..."}]`。
*   **解決方案**：
    *   實作 **「多段式解析」** 邏輯。
    *   依序嘗試：純陣列解析 -> 物件欄位提取 -> 智慧型欄位（text/candidate/value）過濾。
    *   **結果**：極大提升了輸入法建議的穩定性，避免因模型微小變動導致 UI 空白。
