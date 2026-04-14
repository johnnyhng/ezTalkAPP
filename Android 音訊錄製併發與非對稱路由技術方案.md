# **Android 音訊錄製併發與非對稱路由技術方案**

## **1\. 背景與挑戰**

在開發 ezTalk 這類需要實時處理麥克風訊號（如 TSE 技術）的 App 時，通常會遇到兩個核心技術瓶頸：

1. **音訊錄製併發 (Audio Concurrency)**：當系統螢幕錄影與 App 同時請求麥克風權限時，可能導致音訊流中斷或靜音。  
2. **非對稱路由 (Asymmetric Audio Routing)**：預設情況下，Android 會將輸入（麥克風）與輸出（播放）綁定在同一裝置（例如：全藍牙或全手機），手動指定「藍牙入、手機出」需要底層 API 操作。

## **2\. 解決方案：音訊錄製併發處理**

自 Android 10 (API 29\) 起，系統引入了更靈活的共享錄音機制。

### **2.1 設定音訊捕捉策略 (Capture Policy)**

為了讓系統螢幕錄影能「捕捉」到你 App 正在處理的音訊，你必須在 AudioAttributes 中明確聲明。

val audioAttributes \= AudioAttributes.Builder()  
    .setUsage(AudioAttributes.USAGE\_VOICE\_COMMUNICATION) // 或 USAGE\_MEDIA  
    .setContentType(AudioAttributes.CONTENT\_TYPE\_SPEECH)  
    // 關鍵：允許其他應用（如系統螢幕錄影）捕捉此音訊流  
    .setAllowedCapturePolicy(AudioAttributes.ALLOW\_CAPTURE\_BY\_ALL)  
    .build()

### **2.2 使用正確的音訊來源 (Audio Source)**

建議使用 MediaRecorder.AudioSource.VOICE\_RECOGNITION，這在多個 App 競爭麥克風時通常具有較好的相容性與預設處理邏輯。

## **3\. 解決方案：指定非對稱音訊路由**

要達成「Pixel Buds 錄音、手機揚聲器播音」，需要利用 Android 13 (API 33\) 提供的 CommunicationDevice 相關 API。

### **3.1 步驟一：偵測並過濾裝置**

首先，從 AudioManager 獲取所有連接的裝置資訊。

val audioManager \= getSystemService(Context.AUDIO\_SERVICE) as AudioManager  
val inputs \= audioManager.getDevices(AudioManager.GET\_DEVICES\_INPUTS)  
val outputs \= audioManager.getDevices(AudioManager.GET\_DEVICES\_OUTPUTS)

// 尋找 Pixel Buds (TYPE\_BLUETOOTH\_SCO 或 TYPE\_BLUETOOTH\_A2DP)  
val budsMic \= inputs.find { it.type \== AudioDeviceInfo.TYPE\_BLUETOOTH\_SCO }  
// 尋找手機揚聲器  
val phoneSpeaker \= outputs.find { it.type \== AudioDeviceInfo.TYPE\_BUILTIN\_SPEAKER }

### **3.2 步驟二：手動指定偏好裝置**

針對 AudioRecord (錄音) 與 AudioTrack (播音) 分別設定。

* **錄音端**：  
  audioRecord.setPreferredDevice(budsMic)

* **播放端**：  
  audioTrack.setPreferredDevice(phoneSpeaker)

### **3.3 步驟三：管理通訊裝置 (Android 13+ 推薦)**

如果上述設定因系統策略失效，應使用 setCommunicationDevice：

if (phoneSpeaker \!= null) {  
    audioManager.setCommunicationDevice(phoneSpeaker)  
    // 這會強制通訊類的音訊輸出走手機揚聲器  
}

## **4\. 針對 ezTalk (TSE 技術) 的進階建議**

由於 Johnny 的 App 涉及到人聲提取技術，建議採取以下架構以確保穩定性：

1. **內置虛擬混音器 (Virtual Mixer)**：  
   不要依賴系統錄影來抓麥克風，而是在 ezTalk 內部同時啟動 MediaProjection API。將「原始麥克風聲音」與「TSE 處理後的聲音」在 App 內部混音後，直接寫入錄影文件的音軌中。  
2. **處理藍牙 SCO 頻寬限制**：  
   當使用 Pixel Buds 的麥克風時，藍牙通常會切換到 **SCO 模式**。SCO 的採樣率通常被限制在 ![][image1] 或 ![][image2] (Wideband Speech)。這可能會影響 TSE 算法的表現，建議在算法輸入端加入重採樣 (Resampling) 邏輯。  
3. **延遲補償 (Latency Compensation)**：  
   藍牙輸入與手機揚聲器輸出之間存在顯著的時間差。在錄影存檔時，需手動計算 AudioTimestamp 並校正音畫同步。

## **5\. 總結**

| 需求項目 | 核心 API / 設定 | 備註 |
| :---- | :---- | :---- |
| **螢幕錄影不中斷** | setAllowedCapturePolicy(ALLOW\_CAPTURE\_BY\_ALL) | 需在音訊流初始化時設定 |
| **指定 Buds 錄音** | AudioRecord.setPreferredDevice(bluetoothDevice) | 需過濾 GET\_DEVICES\_INPUTS |
| **指定手機播音** | audioManager.setCommunicationDevice(speakerDevice) | Android 13+ 最穩定方案 |

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACsAAAAXCAYAAACS5bYWAAACxElEQVR4Xu2WTYjMYRzHZ7xuXoqYZs3bf8yMpiYHzEkcxImSo5sLJRcOrLJSLEpysQ6ObFOLdVkvmcOIFslbag9isZQDSomtpUjr85t5nvn/9jFjGLn9v/XteX6v/+88z/N//hMKBQjgw/O85el0+gLjVTiQSqV24p4qsXw+PxffAXgE/2mntA7qO8iZaMDHEmc81yA2kUwml7q9moKCRYgYghnjCvPgy/h7xWA+D57EHmN87lc2BjlpI+Qd5rQG8Vsmvt6NtQRFPbBL+xKJxBJ83yKRyByVN/AnYmOx2EIjZtSNCfCXJU6vNW6sJSjqY1XPa188Hl8gDWW0Puz+vxHbLPdfxR4yK3GReYf4mO/mBwzqPHylZgI0/qtYs+XjRvAwIvcy3qVZp87TYqnJmXzLss1rR6xXO4rS5wt8CK8b+yPhsF9dS14LP5sE4YmQ83J4zspi3+SH3YerisXidOtXZ/a3dMT2wlPUzhJbbiOTs8/m1EHguBwH2GebUXDGySnBV2a+DZZtc402V1YWYpnMeU8S2J9gJdRgVfcj7Ky1ZaXwvTSCN6o8Efuaptslls1mkzam0abYw3KfMw0zvwbHuX+zflWoKmw+gTG4QvuxFxv/UeUTsV/hHWmmf6BGO2It6LlLYrBbbPlg1I+YGBKMRqOzJ1WF6ltzTNs0eyN3r/ilDnu1rhGoM/vCjQmaic1kMimvtkCPrEDmlVwuN7OaYD6lslq/fE3w3YAblN3vmU+nqRuFw4VCYYZfNUls9Xy7aCJWtl/83z21y8yfqJzqPbsD53tWaQvmFFk57D2w5ORdwvdUcsRmvtU89KDOY7diRuxb7bfwzLXE89YpX5ep6bE++m7CHrF2HfIiwUGC9+AVEjfbGPNO+MA0Ez6DK9P+N144wjbKfW1tzZZ/ZBg/yBwNQ4wV0/sHvO2rDBAgQADBT4bGLcTXuoI9AAAAAElFTkSuQmCC>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADUAAAAXCAYAAACrggdNAAADLUlEQVR4Xu1WW4hNURieMaFcUjhOzmWvfS4cDqKOPKkRKYqi4Ulh5kGaqSHl9jBJSE2RB03jibwoFONN08wDKU+SXJLLTOLJqdHU0Iw0vm/2v/asWe1tZnsQ2V/97fV///+vtb619l571dTEiPFXoVYplbNJjWKxONNxnGPI6YV1w9pB1zEGfhv8Njw7YZutUh+u6z5A3qhtqVRqIZ7NNi/WbPczKbLZbAqD7VDeRO/YcaJUKs1VnpjrcOsKhcIitN+g7iDjaO+EPeIkwO2zym1MQ94ryV1jB8GfYwyLc96OTQkobEXHz9DJZdhImCjw7bAqd4u+7AwndUXn5HK51VMUxf6eMBcLWrBjqD/JGKzNjkUGOhkKEpXJZOaD/wbr0FwymZzNhcCkVmoOQldEFYVc1479EVGcJAfhrtoxE8hb9s+IAndBRB3AgF2u97Hfxi6tNfNsUci/JpMbM/jrdW5UUZVKZbrRzwc8e2BvxT9k1k+AChGFoqvS4WvYcuH2oj1ovn4BojbB/47ncR4sOo9QImoS80XJwTTA75Z+IpGYA/89rN/uewJUiChwN2SQiwbN4/8z7JYmtChYEwZPyo5uNWp8qIg7JX37Yyl5e8DXay4QSBrCqt4N4Dv0ZC3+JWyEq0bfEHUU9tw1TkYbKqKofD6/FH4L23yN0f6BZ+d4VQhUuKgzHASx7Sbver8CDr5YfC1qwPV2aZicWaOhIorSkP/lO9indDq9gAuK03mJnecDiUPosCuAb+AgELXL4sd+oDze6WtReB7m64f2IGrumzUaWpQKuMH8ShRil2Quu8XfAP+snedDRN2zeVxhZilv9U8YNL+pKqxXE478p2AN4reKv2e8zIMSUVjloh0LEwW+XvibmkO7CXbEzPPBm4LyfrA9cGvtODpsRKwPJ01W/P3wP2JSq3QOdmcdB+XJKBSvQ3xV/DoNcC8kt2zyBLhTjGGM05rDNzUPXD+syreAXLlcngH/oTGeB5AbYY9F0KgYJ9KtDwAjt0ViTxnHoK6OOd5F94vUD/MjRniL0edXTtL9zQut3nXH+0fxjkrrI8dxjGnGiBEjxn+InwhvYNfmBqa5AAAAAElFTkSuQmCC>