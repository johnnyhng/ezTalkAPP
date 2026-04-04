package tw.com.johnnyhng.eztalk.asr.utils

internal object BackendEndpoints {
    private const val PROCESS_AUDIO_SEGMENT = "process_audio"
    private const val UPDATES_SEGMENT = "updates"
    private const val TRANSFER_SEGMENT = "transfer"
    private const val LIST_MODELS_SEGMENT = "list_models"
    private const val CHECK_UPDATE_SEGMENT = "check_update"
    private const val FILES_SEGMENT = "files"

    fun apiBaseUrl(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    fun processAudio(baseUrl: String): String =
        buildEndpoint(baseUrl, PROCESS_AUDIO_SEGMENT)

    fun updates(baseUrl: String): String =
        buildEndpoint(baseUrl, UPDATES_SEGMENT)

    fun transfer(baseUrl: String): String =
        buildEndpoint(baseUrl, TRANSFER_SEGMENT)

    fun listModels(baseUrl: String, userId: String): String =
        buildEndpoint(baseUrl, LIST_MODELS_SEGMENT, userId.substringBefore("@"))

    fun checkUpdate(baseUrl: String, userId: String): String =
        buildEndpoint(baseUrl, CHECK_UPDATE_SEGMENT, userId.substringBefore("@"))

    fun downloadFile(baseUrl: String, userId: String, modelName: String, filename: String): String =
        buildEndpoint(baseUrl, FILES_SEGMENT, userId.substringBefore("@"), modelName, filename)

    private fun buildEndpoint(baseUrl: String, vararg segments: String): String {
        val apiBase = apiBaseUrl(baseUrl)
        if (apiBase.isBlank()) return ""
        return buildList {
            add(apiBase)
            addAll(segments)
        }.joinToString("/")
    }
}
