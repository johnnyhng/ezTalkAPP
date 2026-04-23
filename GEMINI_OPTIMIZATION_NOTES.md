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

### 問題：併發邏輯誤寫為串行導致延遲翻倍
*   **現象**：即使使用了 `async`，若將 `withTimeoutOrNull` 或 `await` 放在錯誤的位置，會導致兩個原本應該併發的請求變成順序執行（Serial Execution）。
*   **教訓**：在 `ExperimentViewModel` 中，必須確保多個 `withTimeoutOrNull` 塊是被封裝在各自獨立的 `async { ... }` 作用域內。
*   **結果**：修正後總延遲從 `3s + 3s = 6s` 降回 `max(3s, 3s) = 3s`。

---

## 4. 指令複雜度與「隱形思考」開銷

### 觀察：複雜分詞與標點指令會激增 Internal Tokens
*   **現象**：在 `Sentence` 模式下，要求「語義分詞 (`|`)」且「標點符號必須獨立」時，模型內部的 Thoughts Tokens 曾飆升至 **近 1000 個**。
*   **分析**：雖然 Flash 模型處理速度快，但極高的指令複雜度會迫使模型進行長距離的邏輯規劃，這會顯著延後 TTFT (Time To First Token)。
*   **解決方案**：
    *   **精簡 Prompt**：移除冗長的場景背景與重複描述，改為極簡清單式指令。
    *   **適度寬容**：與其要求極致的分詞，不如在 Android 端進行簡單的後處理校正。
*   **結果**：成功將隱形思考開銷控制在合理範圍，並維持了漸進式組句的互動體驗。

---

## 5. OAuth 認證效能 (OAuth Performance)

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
