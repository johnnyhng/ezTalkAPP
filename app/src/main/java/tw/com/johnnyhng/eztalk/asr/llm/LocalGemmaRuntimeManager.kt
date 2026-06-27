package tw.com.johnnyhng.eztalk.asr.llm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.com.johnnyhng.eztalk.asr.data.classes.UserSettings

internal sealed interface LocalGemmaRuntimeState {
    data object Idle : LocalGemmaRuntimeState

    data class Loading(
        val modelName: String,
        val backend: LocalGemmaBackend
    ) : LocalGemmaRuntimeState

    data class Ready(
        val modelName: String,
        val backend: LocalGemmaBackend
    ) : LocalGemmaRuntimeState

    data class Error(
        val modelName: String,
        val backend: LocalGemmaBackend,
        val message: String
    ) : LocalGemmaRuntimeState
}

internal object LocalGemmaRuntimeManager {
    private data class RuntimeKey(
        val modelPath: String,
        val backend: LocalGemmaBackend
    )

    @Volatile
    private var activeKey: RuntimeKey? = null

    @Volatile
    private var activeProvider: LocalGemmaLitertLmLlmProvider? = null

    private val _state = MutableStateFlow<LocalGemmaRuntimeState>(LocalGemmaRuntimeState.Idle)
    val state: StateFlow<LocalGemmaRuntimeState> = _state.asStateFlow()

    fun getOrCreateProvider(
        context: Context,
        modelPath: String,
        backend: LocalGemmaBackend
    ): LocalGemmaLitertLmLlmProvider {
        val appContext = context.applicationContext
        val requestedKey = RuntimeKey(modelPath = modelPath, backend = backend)
        activeProvider?.takeIf { activeKey == requestedKey }?.let { return it }

        return synchronized(this) {
            activeProvider?.takeIf { activeKey == requestedKey }?.let { return it }

            safeLogInfo(
                LLM_LOG_TAG,
                "Local Gemma shared runtime creating provider path=$modelPath backend=${backend.storageValue}"
            )
            LocalGemmaLitertLmLlmProvider(
                context = appContext,
                modelPath = modelPath,
                backend = backend
            ).also { provider ->
                activeKey = requestedKey
                activeProvider = provider
            }
        }
    }

    fun getProviderForSettings(
        context: Context,
        settings: UserSettings
    ): LocalGemmaLitertLmLlmProvider? {
        val model = LocalGemmaModelManager(context).resolveModel(settings.selectedLocalGemmaModelName)
            ?: return null
        return getOrCreateProvider(
            context = context,
            modelPath = model.path,
            backend = LocalGemmaBackend.fromStorageValue(settings.localGemmaBackend)
        )
    }

    suspend fun warmUpIfConfigured(
        context: Context,
        settings: UserSettings
    ): Result<Unit> {
        val executionMode = SpeakerLlmExecutionMode.fromStorageValue(settings.speakerLlmExecutionMode)
        if (executionMode == SpeakerLlmExecutionMode.CLOUD) {
            _state.value = LocalGemmaRuntimeState.Idle
            return Result.success(Unit)
        }

        val backend = LocalGemmaBackend.fromStorageValue(settings.localGemmaBackend)
        val modelManager = LocalGemmaModelManager(context)
        val model = modelManager.resolveModel(settings.selectedLocalGemmaModelName)

        if (model == null) {
            if (executionMode == SpeakerLlmExecutionMode.AUTO_LOCAL) {
                _state.value = LocalGemmaRuntimeState.Idle
                safeLogInfo(
                    LLM_LOG_TAG,
                    "Local Gemma warm-up skipped in auto mode: model=${settings.selectedLocalGemmaModelName} unavailable"
                )
                return Result.success(Unit)
            }

            val message = "Local Gemma model not found: ${settings.selectedLocalGemmaModelName}"
            _state.value = LocalGemmaRuntimeState.Error(
                modelName = settings.selectedLocalGemmaModelName,
                backend = backend,
                message = message
            )
            safeLogWarning(LLM_LOG_TAG, message)
            return Result.failure(LlmError.ProviderFailure(message))
        }

        return warmUp(
            context = context,
            modelName = model.name,
            modelPath = model.path,
            backend = backend,
            executionMode = executionMode
        )
    }

    private suspend fun warmUp(
        context: Context,
        modelName: String,
        modelPath: String,
        backend: LocalGemmaBackend,
        executionMode: SpeakerLlmExecutionMode
    ): Result<Unit> {
        _state.value = LocalGemmaRuntimeState.Loading(modelName = modelName, backend = backend)
        safeLogInfo(
            LLM_LOG_TAG,
            "Local Gemma shared warm-up start model=$modelName path=$modelPath backend=${backend.storageValue}"
        )

        val provider = getOrCreateProvider(
            context = context,
            modelPath = modelPath,
            backend = backend
        )

        return provider.warmUp()
            .onSuccess {
                safeLogInfo(
                    LLM_LOG_TAG,
                    "Local Gemma shared warm-up ready model=$modelName backend=${backend.storageValue}"
                )
                _state.value = LocalGemmaRuntimeState.Ready(modelName = modelName, backend = backend)
            }
            .onFailure { error ->
                val message = error.message ?: error.javaClass.simpleName
                safeLogWarning(
                    LLM_LOG_TAG,
                    "Local Gemma shared warm-up failed model=$modelName backend=${backend.storageValue}",
                    error
                )
                _state.value = if (executionMode == SpeakerLlmExecutionMode.AUTO_LOCAL) {
                    safeLogWarning(
                        LLM_LOG_TAG,
                        "Local Gemma warm-up failed in auto mode; cloud LLM fallback remains available"
                    )
                    LocalGemmaRuntimeState.Idle
                } else {
                    LocalGemmaRuntimeState.Error(
                        modelName = modelName,
                        backend = backend,
                        message = message
                    )
                }
            }
    }
}
