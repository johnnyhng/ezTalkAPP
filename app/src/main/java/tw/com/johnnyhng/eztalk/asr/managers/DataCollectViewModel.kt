package tw.com.johnnyhng.eztalk.asr.managers

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.com.johnnyhng.eztalk.asr.utils.deleteQueueState
import tw.com.johnnyhng.eztalk.asr.utils.restoreQueueState
import tw.com.johnnyhng.eztalk.asr.utils.saveQueueState
import tw.com.johnnyhng.eztalk.asr.data.classes.QueueState

data class DataCollectUiState(
    val text: String = "",
    val isSequenceMode: Boolean = false,
    val showNoQueueMessage: Boolean = false,
    val remainingCount: Int = 0,
    val previousCount: Int = 0,
)

class DataCollectViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val appContext = application.applicationContext

    private val queue = ArrayDeque<String>()
    private val history = ArrayDeque<String>()

    private val _uiState = MutableStateFlow(DataCollectUiState())
    val uiState: StateFlow<DataCollectUiState> = _uiState.asStateFlow()

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
        if (queue.isNotEmpty()) {
            val current = _uiState.value.text
            if (current.isNotBlank()) {
                history.addLast(current)
            }

            while (queue.isNotEmpty() && queue.first().isBlank()) {
                queue.removeFirst()
            }

            if (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                _uiState.update {
                    it.copy(
                        text = next,
                        isSequenceMode = true,
                        showNoQueueMessage = false,
                        remainingCount = queue.count { item -> item.isNotBlank() },
                        previousCount = history.size
                    )
                }
                persistQueueState()
                return
            }
        }

        _uiState.update {
            it.copy(
                text = "",
                isSequenceMode = false,
                remainingCount = 0,
                previousCount = history.size
            )
        }
        viewModelScope.launch {
            deleteQueueState(appContext, currentUserId())
        }
    }

    fun moveToPrevious() {
        if (history.isEmpty()) return

        val current = _uiState.value.text
        if (current.isNotBlank()) {
            queue.addFirst(current)
        }
        val previous = history.removeLast()
        _uiState.update {
            it.copy(
                text = previous,
                isSequenceMode = true,
                showNoQueueMessage = false,
                remainingCount = queue.size,
                previousCount = history.size
            )
        }
        persistQueueState()
    }

    private fun applyImportedLines(lines: List<String>) {
        queue.clear()
        queue.addAll(lines.drop(1))
        history.clear()

            _uiState.update {
                it.copy(
                    text = lines.first(),
                    isSequenceMode = true,
                    showNoQueueMessage = false,
                    remainingCount = queue.count { item -> item.isNotBlank() },
                    previousCount = history.size
                )
            }
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
}
