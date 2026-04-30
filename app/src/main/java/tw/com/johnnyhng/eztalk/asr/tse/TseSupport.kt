package tw.com.johnnyhng.eztalk.asr.tse

import android.content.Context
import android.util.Log
import tw.com.johnnyhng.eztalk.asr.TAG
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal data class TseAssetPaths(
    val modelPath: String,
    val dvectorPath: String
)

internal fun ensureTseAssetsForUser(
    context: Context,
    userId: String
): TseAssetPaths {
    val targetDir = File(context.filesDir, "$userId/speaker_id")
    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    fun copyAsset(assetName: String): String {
        val targetFile = File(targetDir, assetName)
        if (!targetFile.exists()) {
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy TSE asset for user=$userId: $assetName", e)
                throw e
            }
        }
        return targetFile.absolutePath
    }

    return TseAssetPaths(
        modelPath = copyAsset("voice_filter_int8.onnx"),
        dvectorPath = copyAsset("dvector.bin")
    )
}

internal fun initializeNativeTseForUser(
    context: Context,
    userId: String,
    nativeTse: NativeTSE = NativeTSE()
): NativeTSE? {
    return try {
        val (modelPath, dvectorPath) = ensureTseAssetsForUser(context, userId)
        val initialized = nativeTse.init(modelPath, dvectorPath)
        if (initialized) {
            Log.i(
                TAG,
                "NativeTSE initialized for user=$userId, modelPath=$modelPath, dvectorPath=$dvectorPath"
            )
            nativeTse
        } else {
            Log.w(TAG, "NativeTSE init returned false for user=$userId")
            nativeTse.release()
            null
        }
    } catch (t: Throwable) {
        Log.e(TAG, "NativeTSE initialization failed for user=$userId", t)
        runCatching { nativeTse.release() }
        null
    }
}
