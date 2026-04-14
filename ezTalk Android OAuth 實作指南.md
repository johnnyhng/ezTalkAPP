# **ezTalk Android OAuth 2.0 實作指南**

本文件針對 ezTalk APP 在 Android Native 環境下，直接實作 OAuth 2.0 授權流程，以實現「使用者登入即使用個人 Gemini 配額」的目標。

## **1\. Google Cloud Console 設定**

在開始寫程式前，必須在 GCP 控制台完成以下配置：

1. **建立 OAuth Client ID**：  
   * 類型選擇 **Android**。  
   * 填入你的 Package Name (例如 com.johnnyhng.eztalk)。  
   * 填入你的 **SHA-1 憑證指紋** (開發時用 debug.keystore，發佈時用 release key)。  
2. **設定 OAuth 同意畫面 (Consent Screen)**：  
   * 確保應用程式狀態為「測試中」或「已發佈」。  
   * **關鍵步驟**：加入範圍 (Scopes) https://www.googleapis.com/auth/generative-language。  
3. **獲取 Web Client ID**：  
   * 即使是 Android App，通常也需要一個「Web 類型」的 Client ID 來交換 ID Token 或傳遞給後端（若有需要）。

## **2\. Android 專案依賴項**

在 app/build.gradle 中加入必要的 SDK：

dependencies {  
    // Google 身分驗證與認證管理  
    implementation "androidx.credentials:credentials:1.2.1"  
    implementation "androidx.credentials:credentials-play-services-auth:1.2.1"  
    implementation "com.google.android.libraries.identity.googleid:googleid:1.1.0"  
      
    // 用於獲取 Access Token 的核心庫  
    implementation "com.google.android.gms:play-services-auth:21.0.0"  
}

## **3\. 獲取 Access Token 與 ID Token**

在 Android 中，ID Token 用於辨識身分，而呼叫 Gemini API 真正需要的是具備 Scopes 權限的 Access Token。

### **核心實作邏輯 (Kotlin)**

import com.google.android.gms.auth.GoogleAuthUtil  
import com.google.android.gms.auth.api.signin.GoogleSignIn  
import android.accounts.Account

// 定義 Gemini API 的 Scope  
private const val GEMINI\_SCOPE \= "oauth2:\[https://www.googleapis.com/auth/generative-language\](https://www.googleapis.com/auth/generative-language)"

/\*\*  
 \* 獲取 Access Token 的非同步函數  
 \*/  
suspend fun fetchAccessToken(context: Context): String? {  
    return withContext(Dispatchers.IO) {  
        try {  
            // 1\. 獲取當前登入的 Google 帳戶  
            val account \= GoogleSignIn.getLastSignedInAccount(context)?.account   
                ?: return@withContext null

            // 2\. 使用 GoogleAuthUtil 獲取具備 Scope 權限的 Access Token  
            // 註：此方法會自動處理緩存，若 Token 已過期，它會嘗試靜默刷新  
            GoogleAuthUtil.getToken(context, account, GEMINI\_SCOPE)  
        } catch (e: UserRecoverableAuthException) {  
            // 需要使用者手動介入（例如重新授權）  
            // 應在此觸發 e.intent 跳轉授權畫面  
            null  
        } catch (e: Exception) {  
            e.printStackTrace()  
            null  
        }  
    }  
}

## **4\. 自動刷新 (Auto-Refresh) 機制**

Android 的 GoogleAuthUtil.getToken() 內建了基本的緩存管理。但為了確保在發送 API 請求時 Token 永遠有效，建議採用以下「主動失效」策略：

### **令牌管理策略**

1. **主動呼叫**：每次發送 Gemini API 請求前，都呼叫 fetchAccessToken()。  
2. **處理過期 (401 Error)**：如果 API 回傳 401 Unauthorized，表示緩存的 Token 已失效。  
3. **強制更新**：呼叫 GoogleAuthUtil.invalidateToken(context, token) 清除本地快取，隨後再次呼叫 getToken()，此時 SDK 會自動連網獲取全新的 Access Token。

fun handleApiCallWithRetry(context: Context) {  
    CoroutineScope(Dispatchers.Main).launch {  
        var token \= fetchAccessToken(context)  
          
        // 嘗試呼叫 API  
        val response \= callGeminiApi(token)  
          
        if (response.code \== 401\) {  
            // Token 失效，清除快取並重試一次  
            withContext(Dispatchers.IO) {  
                GoogleAuthUtil.invalidateToken(context, token)  
            }  
            token \= fetchAccessToken(context)  
            callGeminiApi(token) // 重新請求  
        }  
    }  
}

## **5\. 如何呼叫 Gemini API (OAuth 版)**

獲取 Token 後，不再使用 ?key=YOUR\_API\_KEY，而是將其放入 Header：

POST \[https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent\](https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent)

Header:  
Authorization: Bearer \<YOUR\_ACCESS\_TOKEN\>  
Content-Type: application/json

Body:  
{  
  "contents": \[{"parts": \[{"text": "請解析這段語音意圖..."}\]}\]  
}

## **6\. 技術總結**

* **安全性**：不儲存 Client Secret，完全依賴 Android 系統的安全存儲（Account Manager）。  
* **配額歸屬**：API 呼叫產生的配額消耗將直接計入「使用者登入的帳號」，開發者不需負擔任何費用。  
* **開發者建議**：在 Pixel 7/10 上，利用 Credential Manager 能提供更流暢的 One-tap 登入體驗。