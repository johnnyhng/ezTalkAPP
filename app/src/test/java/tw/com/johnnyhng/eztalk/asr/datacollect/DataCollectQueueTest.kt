package tw.com.johnnyhng.eztalk.asr.datacollect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCollectQueueTest {
    @Test
    fun applyImportedLinesUsesFirstLineAsCurrentAndQueuesTheRest() {
        val result = applyImportedLines(listOf("one", "two", "three"))

        assertEquals("one", result.currentText)
        assertEquals(listOf("two", "three"), result.queue)
        assertTrue(result.history.isEmpty())
        assertEquals(2, result.uiState.remainingCount)
        assertEquals(0, result.uiState.previousCount)
        assertTrue(result.uiState.isSequenceMode)
    }

    @Test
    fun applyImportedLinesReturnsEmptySequenceStateWhenNoLinesExist() {
        val result = applyImportedLines(emptyList())

        assertEquals("", result.currentText)
        assertTrue(result.queue.isEmpty())
        assertTrue(result.history.isEmpty())
        assertEquals(0, result.uiState.remainingCount)
        assertEquals(0, result.uiState.previousCount)
        assertFalse(result.uiState.isSequenceMode)
    }

    @Test
    fun applyImportedLinesCountsOnlyNonBlankRemainingQueueItems() {
        val result = applyImportedLines(listOf("one", "", "two", " "))

        assertEquals("one", result.currentText)
        assertEquals(listOf("", "two", " "), result.queue)
        assertEquals(1, result.uiState.remainingCount)
        assertTrue(result.uiState.isSequenceMode)
    }

    @Test
    fun moveToNextAdvancesAndSkipsBlankQueueEntries() {
        val result = moveToNext(
            currentText = "current",
            queue = listOf("", " ", "next", "later"),
            history = emptyList()
        )

        assertEquals("next", result.currentText)
        assertEquals(listOf("later"), result.queue)
        assertEquals(listOf("current"), result.history)
        assertEquals(1, result.uiState.remainingCount)
        assertEquals(1, result.uiState.previousCount)
        assertTrue(result.uiState.isSequenceMode)
    }

    @Test
    fun moveToNextEndsSequenceWhenQueueIsExhausted() {
        val result = moveToNext(
            currentText = "last",
            queue = emptyList(),
            history = listOf("first")
        )

        assertEquals("", result.currentText)
        assertTrue(result.queue.isEmpty())
        assertEquals(listOf("first", "last"), result.history)
        assertFalse(result.uiState.isSequenceMode)
        assertEquals(0, result.uiState.remainingCount)
        assertEquals(2, result.uiState.previousCount)
    }

    @Test
    fun moveToNextDoesNotAppendBlankCurrentTextToHistory() {
        val result = moveToNext(
            currentText = " ",
            queue = listOf("next"),
            history = listOf("first")
        )

        assertEquals("next", result.currentText)
        assertEquals(listOf("first"), result.history)
        assertTrue(result.queue.isEmpty())
        assertEquals(1, result.uiState.previousCount)
    }

    @Test
    fun moveToNextCountsOnlyNonBlankRemainingItemsAfterAdvance() {
        val result = moveToNext(
            currentText = "current",
            queue = listOf("next", "", "later"),
            history = emptyList()
        )

        assertEquals("next", result.currentText)
        assertEquals(listOf("", "later"), result.queue)
        assertEquals(1, result.uiState.remainingCount)
        assertEquals(1, result.uiState.previousCount)
    }

    @Test
    fun moveToNextEndsSequenceWhenOnlyBlankQueueEntriesRemain() {
        val result = moveToNext(
            currentText = "current",
            queue = listOf("", " "),
            history = listOf("first")
        )

        assertEquals("", result.currentText)
        assertTrue(result.queue.isEmpty())
        assertEquals(listOf("first", "current"), result.history)
        assertFalse(result.uiState.isSequenceMode)
        assertEquals(0, result.uiState.remainingCount)
        assertEquals(2, result.uiState.previousCount)
    }

    @Test
    fun moveToPreviousRestoresPreviousItemAndPushesCurrentBackToQueue() {
        val result = moveToPrevious(
            currentText = "current",
            queue = listOf("later"),
            history = listOf("first", "previous")
        )

        assertEquals("previous", result.currentText)
        assertEquals(listOf("current", "later"), result.queue)
        assertEquals(listOf("first"), result.history)
        assertEquals(2, result.uiState.remainingCount)
        assertEquals(1, result.uiState.previousCount)
        assertTrue(result.uiState.isSequenceMode)
    }

    @Test
    fun moveToPreviousWithEmptyHistoryKeepsStateUnchanged() {
        val result = moveToPrevious(
            currentText = "current",
            queue = listOf("later"),
            history = emptyList()
        )

        assertEquals("current", result.currentText)
        assertEquals(listOf("later"), result.queue)
        assertTrue(result.history.isEmpty())
        assertEquals(1, result.uiState.remainingCount)
        assertEquals(0, result.uiState.previousCount)
        assertTrue(result.uiState.isSequenceMode)
    }

    @Test
    fun moveToPreviousDoesNotPushBlankCurrentTextBackToQueue() {
        val result = moveToPrevious(
            currentText = "",
            queue = listOf("later"),
            history = listOf("first", "previous")
        )

        assertEquals("previous", result.currentText)
        assertEquals(listOf("later"), result.queue)
        assertEquals(listOf("first"), result.history)
        assertEquals(1, result.uiState.remainingCount)
        assertEquals(1, result.uiState.previousCount)
    }
}
