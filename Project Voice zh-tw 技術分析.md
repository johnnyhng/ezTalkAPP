# **Google Project Voice (zh-tw-impl) 技術分析報告 (v2)**

這份報告針對 google/project-voice 專案中 zh-tw-impl 分支的候選詞句生成邏輯、Prompt 工程以及 UI 排版設計進行深度分析。

## **1\. 候選詞句的生成與連接方式 (Connection Logic)**

在 zh-tw 的實現中，預測的核心目標是 **「降低下一個詞的選擇成本」**。

### **A. 排除式預測 (Exclusionary Prediction)**

系統在生成候選時，會嚴格執行以下規則：

* **去重機制**：候選清單會自動過濾掉目前 Input Buffer 中已存在的詞彙。例如，若使用者已點選「我」，候選清單將顯示「想」、「要」、「覺得」，而不會再次出現「我」。  
* **語意延續**：利用已確認的 ![][image1] 作為上下文，直接預測 ![][image2]。這種設計對於手部不靈活的使用者至關重要，因為它能將「輸入」轉化為連續的「選擇」動作。

### **B. 階層式候選**

1. **補全 (Completion)**：針對拼音/注音殘影進行補全（這部分通常與預測分開處理）。  
2. **預測 (Prediction)**：基於上下文生成的「下一個詞」或「下一個句」，旨在完成使用者的意圖。

## **2\. Prompt 定義分析 (優化版)**

根據 zh-tw-impl 的程式碼結構，Prompt 定義中包含嚴格的輸出過濾指令，以確保候選內容的純淨度。

### **Role & Instruction (核心指令)**

你是一位服務台灣繁體中文使用者的溝通助手。  
請根據給定的上下文 \[Context\]，預測接下來最可能的 5 個詞彙與 3 個句子。  
注意：\[Candidate\] 嚴格禁止包含 \[Context\] 中最後一個完整的詞彙。

### **Constraints (約束條件)**

* **不可重複**：輸出結果必須與輸入內容具有「語法連接性」，但不可有「內容重疊」。  
* **情境感知 (Context-aware)**：若 Context 結束於動詞，則優先預測受詞或副詞。  
* **在地化語法**：確保語助詞（如：啦、喔、呢）符合台灣口語習慣。

### **Prompt 範例**

「Context: 『今天天氣』。請生成 5 個後續詞彙。

正確輸出：『很好』、『很冷』、『轉陰』...

錯誤輸出：『天氣很好』（因為包含重複的『天氣』）」

## **3\. 候選句排版設計 (Layout & Typesetting)**

參考影片中的操作行為，排版設計專注於「視線移動」與「點選精度」的平衡。

### **A. 非重複性佈局**

* **區塊分離**：將「當前輸入區」與「候選預測區」在視覺上完全分離。候選區不顯示任何已輸入的文字，減少視覺干擾。  
* **橫向滾動 vs 網格**：針對較長的預測句，採用橫向長條佈局，確保句子能完整顯示且點擊面積夠大。

### **B. Fitts's Law 的應用**

* **間距優化**：考慮到手部不靈活（Hand tremors/Fine motor limitations），候選詞塊之間的 gap 設定較大，避免在快速連續點選時造成誤觸。  
* **高對比導航**：預測詞句使用深色背景搭配高對比白字，確保在不同光線下皆能快速定位。

## **4\. 未來擴充建議：Web3 語境集成**

針對 Johnny 的背景，若要進一步優化此專案：

* **專業術語權重**：在 LLM 的 Prompt 中加入「區塊鏈/軟體工程」語境包。當偵測到「發送」時，預測詞優先出現「交易」、「代幣」或「簽名」。  
* **零知識證明隱私**：考慮到溝通內容的私密性，可以研究如何將預測邏輯運行在本地客戶端，或透過 ZK 方式處理雲端 LLM 請求，保障使用者的溝通隱私。

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABsAAAAYCAYAAAALQIb7AAAB80lEQVR4Xu2US2gTURSGY9WC+KC0jYN5v2BwdpKdVES3PhDBQleljSgFuxNKJUJX7SqrEBBclmxK8dGCC4MIgiAScKngRhBKF1LQUgIF0e/Ec2E4WDVJN0J++Jl7/v/cc++5M3cikT76+AsGMpnM03Q6/Rn+gN/h61wul4pGo8fwnhNvqvcVNoIgGJSJjKfhjnrfYMUW/y1SqdScTrppPbSaepesx2auoS8nEokj1tsTTJrSgneth7YqHjkTxjoop+L7/nGj/xnJZPKqFKTDpbCOdgFWdSN3wh65s3A8rP0TWOycLvbAacVi8TA7n0S7oZ0tOI/4FPoTF3cEJga6+xWnybHJu0C7qF7VeYzr2WzWd3FHYKKnBRsS02mMDVyRMdoZ9eoS6/sth+d3ikNasCkBz5Iz5Bqo90w39cJ9/l2DIl/gJ3gWnnZ6oVA4oYu9gVU6HgvP6woUeg9bHNNtYx1A34XbsGa8NtCvw8d6X+/DMnwpPwab2waLvCJhg+eQ9dK//jAfpUvrCfDuyTXg+SEWi42q9g6taHPbwHyEecvqArwm3mWrO+Tz+ZPkVNjovMTSEXGLr3nY5u4LWOit3FcZ67E2PM872vPHZCEdUHxLfgQSM34IS9JpPB4fsfk9gaLnKb7uYo58EW0NzoTz+vi/8BMJaYIcMUVRoAAAAABJRU5ErkJggg==>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAC0AAAAYCAYAAABurXSEAAACY0lEQVR4Xu2WS2gTURSGW18gVRFtGprX5AXBLAQJCC1V0a1PBAUpKFarCHUniNJSu6mrrkKhIK6kGxGfoGAUQRBECi4V3QiiuBBBRQqC6HfMuXI5rTCUpOlifviZe/7/zL3nnszcSVtbhAgRlgSWZbPZO0EQvIe/4S/4LJ/PZ2Kx2Bq8h8Sf1PsKa+VyeZXcyHgA/lDvG5ywkzcVmUzmvC5+0npok+rtth6bOoB+LZVKrbZe08Hix7Wwc9ZDuyEeOUeMtVx+pVKptNboi4N0Or1PCqPjl30dbSes6oaGfI/cs/Cwry0qKHqbFj3ltEqlspJOHkM7pJ2+5DzibvTbLm4JKKCs3bzuNHkc5FlF26Ve1XmMp3O5XMnFLQEFxLWwmsR0PsFG9soYbYt60xLr8z/s399gtFPPVrjdGhYrtLAZCbiecIYcf+rd1809dsfeQsHG91tNwNw98Cr+G64D1p8Dkj7Dd7AXbnJ6sVhcp0U/h1V+gT7/voWAOUat5oN1amGLfgVn2eVpY7Wj/4Tf4aTx/gL9ILyl5/0IHIZP5ANlcwX+Sz0fQhfNRE9J/Mh1vfWC+hfzrXTdegK8i3L8cX2dSCQ6VXuJVrG5AtYYs5qP0EWTdJNFTlldgDeDt8fqDoVCoYucCYq5ILF0mHiW02eDxIz7g/ov4PjIj5n7qD+fFv3vvWoaKPiFnPcyDuqPSy0ej3fM99KG7PScvxQNhXSURb7IB0lixlekU9L5ZDK50eaHKZruD1q9oaCIHSx0z8UsOI52F57x8xz+VzRH6mZ9ZD7ABzK2OS0DxfRbLUKEBuAPWCGk0bWte8EAAAAASUVORK5CYII=>