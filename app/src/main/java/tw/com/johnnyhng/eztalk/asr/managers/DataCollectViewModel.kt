package tw.com.johnnyhng.eztalk.asr.managers

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.utils.deleteQueueState
import tw.com.johnnyhng.eztalk.asr.utils.restoreQueueState
import tw.com.johnnyhng.eztalk.asr.utils.saveQueueState
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState
import tw.com.johnnyhng.eztalk.asr.datacollect.applyImportedLines as reduceApplyImportedLines
import tw.com.johnnyhng.eztalk.asr.datacollect.moveToNext as reduceMoveToNext
import tw.com.johnnyhng.eztalk.asr.datacollect.moveToPrevious as reduceMoveToPrevious
import tw.com.johnnyhng.eztalk.asr.tse.ManagedTseMaskPipeline
import tw.com.johnnyhng.eztalk.asr.tse.ManagedTseProbe
import tw.com.johnnyhng.eztalk.asr.tse.ManagedTseWaveformPipeline
import kotlin.math.PI
import kotlin.math.sin

data class DataCollectUiState(
    val text: String = "我在做測試",
    val isSequenceMode: Boolean = false,
    val showNoQueueMessage: Boolean = false,
    val remainingCount: Int = 0,
    val previousCount: Int = 0,
)

class DataCollectViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "DataCollectViewModel"
    }

    private val settingsManager = SettingsManager(application)
    private val appContext = application.applicationContext
    private val managedMicPipeline = ManagedTseMaskPipeline(appContext)
    private val managedMicWaveformPipeline = ManagedTseWaveformPipeline(appContext)
    private val managedMicMutex = Mutex()
    private val managedMicPending = ArrayList<Float>()
    private var managedMicPipelineReady = false
    private var managedMicWaveformPipelineReady = false
    private var managedMicHopCount = 0

    private val queue = ArrayDeque<String>()
    private val history = ArrayDeque<String>()

    private val _uiState = MutableStateFlow(DataCollectUiState())
    val uiState: StateFlow<DataCollectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val probe = ManagedTseProbe(appContext)
            val initialized = probe.initialize()
            val dummyInferenceOk = if (initialized) probe.runDummyInference() else false
            Log.i(
                TAG,
                "ManagedTseProbe startup result: initialized=$initialized dummyInferenceOk=$dummyInferenceOk"
            )
            probe.close()

            val pipeline = ManagedTseMaskPipeline(appContext)
            val pipelineInitialized = pipeline.initialize()
            var processedHops = 0
            var lastMaskStats = "n/a"
            if (pipelineInitialized) {
                repeat(4) { hopIndex ->
                    val hop = FloatArray(160) { sampleIndex ->
                        val t = (hopIndex * 160 + sampleIndex) / 16000.0
                        (0.1 * sin(2.0 * PI * 440.0 * t)).toFloat()
                    }
                    val result = pipeline.processHop(hop) ?: return@repeat
                    processedHops++
                    lastMaskStats = summarizeMask(result.mask)
                }
            }
            Log.i(
                TAG,
                "ManagedTseMaskPipeline startup result: initialized=$pipelineInitialized processedHops=$processedHops lastMaskStats=$lastMaskStats"
            )
            pipeline.close()

            managedMicPipelineReady = managedMicPipeline.initialize()
            Log.i(TAG, "ManagedTseMaskPipeline live probe ready: initialized=$managedMicPipelineReady")

            managedMicWaveformPipelineReady = managedMicWaveformPipeline.initialize()
            Log.i(TAG, "ManagedTseWaveformPipeline live probe ready: initialized=$managedMicWaveformPipelineReady")
        }
    }

    fun onLiveMicSamples(samples: FloatArray) {
        if (samples.isEmpty() || !managedMicWaveformPipelineReady) return
        viewModelScope.launch(Dispatchers.IO) {
            managedMicMutex.withLock {
                managedMicPending.addAll(samples.toList())
                while (managedMicPending.size >= 160) {
                    val hop = FloatArray(160) { idx -> managedMicPending[idx] }
                    managedMicPending.subList(0, 160).clear()
                    val result = managedMicWaveformPipeline.processHop(hop) ?: continue
                    managedMicHopCount++
                    if (managedMicHopCount <= 5 || managedMicHopCount % 25 == 0) {
                        Log.i(
                            TAG,
                            "ManagedTseWaveformPipeline live mic stats: hop=$managedMicHopCount hopRms=${formatRms(hop)} magRms=${formatRms(result.magnitude)} magStats=${summarizeArray(result.magnitude)} mask=${summarizeArray(result.mask)} waveformRms=${formatRms(result.waveformHop)} waveformStats=${summarizeArray(result.waveformHop)}"
                        )
                    }
                }
            }
        }
    }

    fun resetLiveMicProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            managedMicMutex.withLock {
                managedMicPending.clear()
                managedMicHopCount = 0
                managedMicPipeline.reset()
                managedMicWaveformPipeline.reset()
            }
        }
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun clearText() {
        _uiState.update { it.copy(text = "") }
    }

    fun onSequenceModeChange(enabled: Boolean) {
        if (!enabled) {
            _uiState.update { it.copy(isSequenceMode = false, showNoQueueMessage = false) }
            return
        }

        viewModelScope.launch {
            val state = restoreQueueState(appContext, currentUserId())
            if (state == null) {
                _uiState.update { it.copy(isSequenceMode = false, showNoQueueMessage = true) }
                return@launch
            }

            queue.clear()
            queue.addAll(state.queue)
            history.clear()
            _uiState.update {
                it.copy(
                    text = state.currentText,
                    isSequenceMode = true,
                    showNoQueueMessage = false,
                    remainingCount = queue.count { item -> item.isNotBlank() },
                    previousCount = history.size
                )
            }
        }
    }

    fun importFromUri(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch(Dispatchers.IO) {
            val lines = parseLines(uri)
            withContext(Dispatchers.Main) {
                if (lines.isNotEmpty()) {
                    applyImportedLines(lines)
                } else {
                    _uiState.update { it.copy(showNoQueueMessage = true) }
                }
            }
        }
    }

    fun moveToNext() {
        val snapshot = reduceMoveToNext(
            currentText = _uiState.value.text,
            queue = queue.toList(),
            history = history.toList()
        )

        queue.clear()
        queue.addAll(snapshot.queue)
        history.clear()
        history.addAll(snapshot.history)
        _uiState.value = snapshot.uiState

        if (snapshot.uiState.isSequenceMode) {
            persistQueueState()
        } else {
            viewModelScope.launch {
                deleteQueueState(appContext, currentUserId())
            }
        }
    }

    fun skipCurrent() {
        moveToNext()
    }

    fun moveToPrevious() {
        val snapshot = reduceMoveToPrevious(
            currentText = _uiState.value.text,
            queue = queue.toList(),
            history = history.toList()
        )
        if (snapshot.currentText == _uiState.value.text && snapshot.history == history.toList() && snapshot.queue == queue.toList()) {
            return
        }

        queue.clear()
        queue.addAll(snapshot.queue)
        history.clear()
        history.addAll(snapshot.history)
        _uiState.value = snapshot.uiState
        persistQueueState()
    }

    fun retryLastCompleted() {
        moveToPrevious()
    }

    private fun applyImportedLines(lines: List<String>) {
        val snapshot = reduceApplyImportedLines(lines)
        queue.clear()
        queue.addAll(snapshot.queue)
        history.clear()
        history.addAll(snapshot.history)
        _uiState.value = snapshot.uiState
        persistQueueState()
    }

    private fun persistQueueState() {
        viewModelScope.launch {
            saveQueueState(
                context = appContext,
                userId = currentUserId(),
                state = QueueState(
                    currentText = _uiState.value.text,
                    queue = queue.toList()
                )
            )
        }
    }

    private suspend fun currentUserId(): String {
        return settingsManager.userSettings.first().userId
    }

    private fun parseLines(uri: Uri): List<String> {
        val resolver = appContext.contentResolver
        val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return emptyList()
        val isCsv = resolver.getType(uri)?.contains("csv", ignoreCase = true) == true ||
            (uri.lastPathSegment?.lowercase()?.contains(".csv") == true)

        return if (isCsv) {
            text.lineSequence()
                .map { parseCsvRow(it) }
                .filter { it.isNotBlank() }
                .toList()
        } else {
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        }
    }

    private fun parseCsvRow(row: String): String {
        if (row.isBlank()) return ""

        val cells = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val ch = row[i]
            when {
                ch == '"' && i + 1 < row.length && row[i + 1] == '"' -> {
                    builder.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells.add(builder.toString().trim())
                    builder.clear()
                }
                else -> builder.append(ch)
            }
            i++
        }
        cells.add(builder.toString().trim())
        return cells.firstOrNull { it.isNotBlank() } ?: ""
    }

    private fun summarizeMask(mask: FloatArray): String {
        return summarizeArray(mask)
    }

    private fun summarizeArray(values: FloatArray): String {
        if (values.isEmpty()) return "empty"
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0f
        for (value in values) {
            if (value < min) min = value
            if (value > max) max = value
            sum += value
        }
        val avg = sum / values.size
        return "min=${formatRms(min)} avg=${formatRms(avg)} max=${formatRms(max)}"
    }

    private fun formatRms(values: FloatArray): String {
        if (values.isEmpty()) return "0.000000"
        var sum = 0.0
        for (value in values) {
            sum += value * value
        }
        return formatRms(kotlin.math.sqrt(sum / values.size))
    }

    private fun formatRms(value: Double): String {
        return "%.6f".format(value)
    }

    private fun formatRms(value: Float): String {
        return "%.6f".format(value.toDouble())
    }
}
