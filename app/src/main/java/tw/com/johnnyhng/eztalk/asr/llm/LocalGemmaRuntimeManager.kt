package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.com.johnnyhng.eztalk.asr.TAG

internal sealed interface LocalGemmaRuntimeState {
    data object Idle : LocalGemmaRuntimeState
    data class Loading(
        val modelName: String,
        val backend: String
    ) : LocalGemmaRuntimeState
    data class Ready(
        val modelName: String,
        val backend: String
    ) : LocalGemmaRuntimeState
    data class Error(
        val modelName: String,
        val backend: String,
        val message: String
    ) : LocalGemmaRuntimeState
}

internal object LocalGemmaRuntimeManager {
    private data class RuntimeKey(
        val modelPath: String,
        val backend: String
    )

    private val mutex = Mutex()

    @Volatile
    private var activeKey: RuntimeKey? = null

    @Volatile
    private var activeProvider: LocalGemmaLitertLmLlmProvider? = null

    private val _state = MutableStateFlow<LocalGemmaRuntimeState>(LocalGemmaRuntimeState.Idle)
    val state: StateFlow<LocalGemmaRuntimeState> = _state.asStateFlow()

    suspend fun getOrCreateProvider(
        context: Context,
        modelPath: String,
        backend: String
    ): LocalGemmaLitertLmLlmProvider {
        val appContext = context.applicationContext
        val requestedKey = RuntimeKey(modelPath = modelPath, backend = backend)
        activeProvider?.takeIf { activeKey == requestedKey }?.let { return it }

        return mutex.withLock {
            activeProvider?.takeIf { activeKey == requestedKey }?.let { return@withLock it }

            Log.i(TAG, "Local Gemma shared runtime creating provider path=$modelPath backend=$backend")
            LocalGemmaLitertLmLlmProvider(appContext, modelPath, backend).also { provider ->
                activeKey = requestedKey
                activeProvider = provider
                if (_state.value !is LocalGemmaRuntimeState.Loading) {
                    _state.value = LocalGemmaRuntimeState.Idle
                }
            }
        }
    }

    suspend fun warmUp(
        context: Context,
        modelName: String,
        modelPath: String,
        backend: String
    ): Result<Unit> {
        _state.value = LocalGemmaRuntimeState.Loading(modelName = modelName, backend = backend)
        Log.i(TAG, "Local Gemma shared warm-up start model=$modelName path=$modelPath backend=$backend")

        val provider = getOrCreateProvider(
            context = context,
            modelPath = modelPath,
            backend = backend
        )

        return provider.warmUp()
            .onSuccess {
                Log.i(TAG, "Local Gemma shared warm-up ready model=$modelName backend=$backend")
                _state.value = LocalGemmaRuntimeState.Ready(modelName = modelName, backend = backend)
            }
            .onFailure { error ->
                val message = error.message ?: error.javaClass.simpleName
                Log.w(TAG, "Local Gemma shared warm-up failed model=$modelName backend=$backend", error)
                _state.value = LocalGemmaRuntimeState.Error(
                    modelName = modelName,
                    backend = backend,
                    message = message
                )
            }
    }
}
