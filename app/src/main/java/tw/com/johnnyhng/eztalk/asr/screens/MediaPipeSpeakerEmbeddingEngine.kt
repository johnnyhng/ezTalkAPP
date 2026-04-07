package tw.com.johnnyhng.eztalk.asr.screens

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import java.io.Closeable
import tw.com.johnnyhng.eztalk.asr.TAG

internal class MediaPipeSpeakerEmbeddingEngine(
    private val context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH
) : SpeakerEmbeddingEngine, Closeable {

    private val textEmbedder: TextEmbedder? by lazy {
        createTextEmbedder()
    }

    val isAvailable: Boolean
        get() = textEmbedder != null

    override fun embed(text: String): FloatArray {
        if (text.isBlank()) return floatArrayOf()

        val embedder = textEmbedder ?: return floatArrayOf()
        return try {
            val vector = embedder
                .embed(text)
                .embeddingResult()
                .embeddings()
                .firstOrNull()
                ?.floatEmbedding()
                ?: floatArrayOf()

            Log.d(
                TAG,
                "Speaker semantic embedding length=${vector.size} preview=${vector.previewForLog()}"
            )
            vector
        } catch (error: Exception) {
            Log.e(TAG, "Speaker MediaPipe embedding failed", error)
            floatArrayOf()
        }
    }

    override fun close() {
        textEmbedder?.close()
    }

    private fun createTextEmbedder(): TextEmbedder? {
        if (!assetExists(modelAssetPath)) {
            Log.w(
                TAG,
                "Speaker MediaPipe model missing at assets/$modelAssetPath; fallback to lexical search"
            )
            return null
        }

        return try {
            TextEmbedder.createFromFile(context, modelAssetPath).also {
                Log.i(TAG, "Speaker MediaPipe text embedder ready from assets/$modelAssetPath")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Speaker MediaPipe init failed", error)
            null
        }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun FloatArray.previewForLog(maxSize: Int = 8): String {
        if (isEmpty()) return "[]"
        return take(maxSize).joinToString(
            prefix = "[",
            postfix = if (size > maxSize) ", ...]" else "]"
        ) { value ->
            "%.4f".format(value)
        }
    }

    companion object {
        private const val DEFAULT_MODEL_ASSET_PATH = "models/universal_sentence_encoder.tflite"
    }
}
