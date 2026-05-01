# ezTalk TSE 通用加速方案計劃書

## 1. 目的

本文件定義 `ezTalk` 在 Android 上的 Target-Speaker Extraction (`TSE`) 通用加速方案。

目標不是單純讓模型「能跑」，而是為下列路徑建立可落地的加速策略：

`raw mic -> TSE -> VAD -> ASR`

本計劃書要解決的核心問題：

- 不同 Android SoC 的加速路線不同
- `NNAPI attached` 不等於真的吃到硬體
- `Google Tensor` 與 `Qualcomm Snapdragon` 不能共用同一套加速假設
- 模型、runtime、native pipeline 必須一起設計

## 2. 目前結論

### 2.1 主線目標

目前主線優先順序如下：

1. `Google Tensor` 裝置優先
2. `Tensor G2` 作為第一個基線
3. `Qualcomm Snapdragon + QNN` 作為次線

### 2.2 已知事實

從目前實測結果來看：

- 舊版 `VoiceFilter + Bi-LSTM` 不適合 Android realtime
- `VoiceFilter-Lite` 比前一代更接近可用
- `ONNX Runtime + NNAPI` 可以 attach，但可能落到 `nnapi-reference`
- 即使模型能跑，延遲仍可能高於 `10 ms hop` 的 live-path 需求

### 2.3 關鍵判斷

- `QNN` 不是 `Tensor G2` 的答案
- `QNN` 只適用於 Qualcomm/Snapdragon 路線
- `NNAPI` 目前只能視為過渡性驗證手段
- 長期應保留轉向 Tensor-friendly managed runtime 的可能性

## 3. 加速方案總覽

### 3.1 路線 A：Google Tensor 主線

適用對象：

- Pixel / Google Tensor 裝置
- 第一基線：`Tensor G2`

目標：

- 優先確認 Tensor 裝置上的模型可行性
- 建立 Tensor 路徑的 latency / quality / VAD / ASR baseline

現階段原則：

- 繼續用現有 native `ONNX Runtime` 路徑做驗證
- 不把 `NNAPI attached` 當成功條件
- 實際觀察是否落到 `nnapi-reference`
- 若 ORT/NNAPI 持續不穩或太慢，保留轉向 Tensor-native runtime 的空間

### 3.2 路線 B：Snapdragon / QNN 次線

適用對象：

- Qualcomm Snapdragon 裝置

目標：

- 在 Tensor 主線穩定後，再做 Qualcomm 專屬最佳化

現階段原則：

- 使用 `ONNX Runtime + QNN EP` 做 feasibility study
- 只在模型圖夠簡單、量化完整、設備明確時投入
- 不用 QNN 來主導 Tensor 主線的架構選擇

## 4. 模型要求

無論是 Tensor 主線或 QNN 次線，下一代 TSE 模型都應滿足下列條件：

- streaming-first
- 固定 input shape
- 固定 speaker embedding shape
- 無動態 sequence length
- quantization-friendly
- 避免複雜動態 masking/control flow

推薦方向：

- `VoiceFilter-Lite` 類型
- streaming / chunk-based 輕量模型
- 固定 `T` 的 spectrogram-mask 路線

不建議：

- `Bi-LSTM` live path
- 依賴 whole-utterance context 的模型
- attention graph 太動態、難以 export 到 mobile backend 的設計

## 5. Runtime 原則

### 5.1 近程原則

短期內以現有 JNI/native pipeline 為主：

- native DSP
- native ORT session
- `160`-sample hop
- `400`-sample window
- `mask`-based STFT pipeline

目的：

- 快速量測真實裝置表現
- 保持與現有 app 管線相容

### 5.2 長期原則

不要把最終方案鎖死在：

- `NNAPI`
- 單一 execution provider
- 單一 SoC 家族

真正穩定的方案應該具備：

- Tensor-first runtime 可行
- Qualcomm/QNN 可選
- 模型圖足夠簡單，能跨 runtime 遷移

## 6. 驗證流程

### 6.1 Tensor G2 驗證

先依照 Tensor 主線 checklist 驗證：

- model 是否可穩定載入
- session init 是否穩定
- latency 是否接近 realtime
- 是否落到 `nnapi-reference`
- TSE output 是否過度抑制
- downstream VAD/ASR 是否可接受

### 6.2 Snapdragon/QNN 驗證

僅在 Tensor 主線有可用模型後進行：

- QNN runtime 是否可建置
- graph 是否符合 QNN 約束
- 是否有真正 backend offload
- latency 是否顯著優於 CPU/其他路線

## 7. 目前建議執行順序

1. 以 `Tensor G2` 作為主要 baseline
2. 持續驗證 `VoiceFilter-Lite` 類模型
3. 固定測試模板，累積 device/model/runtime baseline
4. 只有在 Tensor 主線方向穩定後，再開 Snapdragon/QNN 評估

## 8. 非目標

本計畫目前不以這些作為成功條件：

- `NNAPI attached`
- 單次看起來比較快的偶發 log
- 假設 `QNN` 可以直接解 Tensor 裝置加速
- 為了單一 Snapdragon 裝置而扭曲整個模型設計

## 9. 對應文件

本計畫與下列文件配合使用：

- [TSE_ROADMAP_INDEX.md](./TSE_ROADMAP_INDEX.md)
- [ANDROID_TSE_ACCELERATION_STRATEGY.md](./ANDROID_TSE_ACCELERATION_STRATEGY.md)
- [TENSOR_G2_VALIDATION_CHECKLIST.md](./TENSOR_G2_VALIDATION_CHECKLIST.md)
- [SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md](./SNAPDRAGON_QNN_FEASIBILITY_CHECKLIST.md)
- [ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md](./ANDROID_REALTIME_TSE_MODEL_REQUIREMENTS.md)
- [JNI_REALTIME_TSE_DESIGN.md](./JNI_REALTIME_TSE_DESIGN.md)

## 10. 一句話結論

`ezTalk` 的 TSE 加速方案應以 `Tensor G2 / Google Tensor` 為第一優先主線，`Snapdragon/QNN` 為第二優先次線，並以固定 shape、streaming-first、quantization-friendly 的模型為共同技術基礎。
