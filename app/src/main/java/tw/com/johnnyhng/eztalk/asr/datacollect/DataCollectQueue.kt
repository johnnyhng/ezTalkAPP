package tw.com.johnnyhng.eztalk.asr.datacollect

import tw.com.johnnyhng.eztalk.asr.managers.DataCollectUiState

internal data class DataCollectQueueSnapshot(
    val currentText: String,
    val queue: List<String>,
    val history: List<String>,
    val uiState: DataCollectUiState
)

internal fun applyImportedLines(lines: List<String>): DataCollectQueueSnapshot {
    val queue = lines.drop(1)
    val history = emptyList<String>()
    return DataCollectQueueSnapshot(
        currentText = lines.firstOrNull().orEmpty(),
        queue = queue,
        history = history,
        uiState = DataCollectUiState(
            text = lines.firstOrNull().orEmpty(),
            isSequenceMode = lines.isNotEmpty(),
            showNoQueueMessage = false,
            remainingCount = queue.count { it.isNotBlank() },
            previousCount = 0
        )
    )
}

internal fun moveToNext(
    currentText: String,
    queue: List<String>,
    history: List<String>
): DataCollectQueueSnapshot {
    val nextHistory = history.toMutableList().apply {
        if (currentText.isNotBlank()) {
            add(currentText)
        }
    }
    val remainingQueue = ArrayDeque(queue)
    while (remainingQueue.isNotEmpty() && remainingQueue.first().isBlank()) {
        remainingQueue.removeFirst()
    }

    if (remainingQueue.isNotEmpty()) {
        val next = remainingQueue.removeFirst()
        return DataCollectQueueSnapshot(
            currentText = next,
            queue = remainingQueue.toList(),
            history = nextHistory,
            uiState = DataCollectUiState(
                text = next,
                isSequenceMode = true,
                showNoQueueMessage = false,
                remainingCount = remainingQueue.count { it.isNotBlank() },
                previousCount = nextHistory.size
            )
        )
    }

    return DataCollectQueueSnapshot(
        currentText = "",
        queue = emptyList(),
        history = nextHistory,
        uiState = DataCollectUiState(
            text = "",
            isSequenceMode = false,
            showNoQueueMessage = false,
            remainingCount = 0,
            previousCount = nextHistory.size
        )
    )
}

internal fun moveToPrevious(
    currentText: String,
    queue: List<String>,
    history: List<String>
): DataCollectQueueSnapshot {
    if (history.isEmpty()) {
        return DataCollectQueueSnapshot(
            currentText = currentText,
            queue = queue,
            history = history,
            uiState = DataCollectUiState(
                text = currentText,
                isSequenceMode = true,
                showNoQueueMessage = false,
                remainingCount = queue.size,
                previousCount = history.size
            )
        )
    }

    val nextQueue = ArrayDeque(queue).apply {
        if (currentText.isNotBlank()) {
            addFirst(currentText)
        }
    }
    val nextHistory = history.toMutableList()
    val previous = nextHistory.removeLast()

    return DataCollectQueueSnapshot(
        currentText = previous,
        queue = nextQueue.toList(),
        history = nextHistory,
        uiState = DataCollectUiState(
            text = previous,
            isSequenceMode = true,
            showNoQueueMessage = false,
            remainingCount = nextQueue.size,
            previousCount = nextHistory.size
        )
    )
}
