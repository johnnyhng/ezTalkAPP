# **ezTalk API V2 介面說明文件 (Android 對接版)**

本文件根據 **tw.com.johnnyhng.eztalk.asr.utils** 實作邏輯進行更新。

## **1\. 基礎資訊**

* **Base URL**: https://120.126.151.159:56432/api/v2  
* **SSL 驗證**: 請在 Android 端 (如 HttpsURLConnection) 設定 HostnameVerifier { \_, \_ \-\> true } 以跳過校驗。  
* **重導向**: 使用 **HTTP 307**，確保 POST/PUT Body 在轉發至舊後端 (:56432) 或新後端 (:56778) 時完整保留。

## **2\. 語音處理入口 (Process Audio)**

* **URL**: POST /api/v2/process\_audio  
* **Android Request Body (JSON)**:  
  {  
    "login\_user": "\<user\_id\>",  
    "filename": "20240418-123456.wav",  
    "label": "修正後的文字",  // 若無則為 "tmp"  
    "raw": \[12, 34, 56, ...\], // 音訊數據  
    "num\_of\_stn": 8  
  }

* **行為**:  
  1. 若有 label (且非 "tmp")，Proxy 會將其視為標籤更新，更新 MongoDB。  
  2. 自動補全 settings 並重組為 {"user":..., "content":..., "settings":...} 轉發至舊後端存檔。

## **3\. 資料修正與標籤更新 (Updates)**

對應 Android 的 putForUpdates 函式。

* **URL**: PUT /api/v2/updates  
* **Android Request Body (JSON)**:  
  {  
    "account": {  
      "user\_id": "\<user\_id\>",  
      "password": "double\_sha256\_hash" // 注意：Android 端需傳送兩次 SHA256 的結果  
    },  
    "streamFilesMove": \[  
      {  
        "20240418-123456.wav": {  
          "original": "tmp",  
          "modified": "正確文字",  
          "candidates": \["候選1", "候選2"\]  
        }  
      }  
    \],  
    "sentence": "正確文字",  
    "update\_files": "True"  
  }

* **Proxy 處理邏輯**:  
  1. **解析 Account**: 從 account.user\_id 取得用戶（會自動 split @ 處理）。  
  2. **DB 更新**: 從 streamFilesMove 提取 candidates 更新至 MongoDB sst 集合。  
  3. **格式重組**: Proxy 會將欄位封裝進 content 物件並補上預設 settings，以符合舊後端 verify() 的規範。

## **4\. 模型管理 (增量更新)**

### **4.1 檢查模型更新**

* **URL**: GET /api/v2/check\_update/\<user\_id\>  
* **回傳**:  
  {  
    "file\_size\_bytes": 239233840,  
    "filename": "model.int8.onnx",  
    "server\_hash": "57ab111...e77e",  
    "user\_id": "\<user\_id\>"  
  }

### **4.2 下載檔案**

* **URL**: GET /api/v2/files/\<user\_id\>/\<model\_name\>/\<filename\>

*文件日期：2024-04-18 (修正密碼為 Double SHA256，更新 UserID 為通用佔位符)*