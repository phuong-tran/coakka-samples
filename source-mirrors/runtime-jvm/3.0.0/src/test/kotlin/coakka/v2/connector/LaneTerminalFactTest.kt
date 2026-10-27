package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaneTerminalFactTest {
    @Test
    fun fileSnapshotUsesTheCoreTransitionNotItsStateLabel() {
        val value = FileTransferSnapshot(
            direction = FileTransferDirection.RECEIVE, state = FileTransferState.PAUSED,
            result = FileTransferResult.NETWORK_IO, expectedSize = 100, transferredBytes = 50,
            committedOffset = 50, progressMilli = 50000, cancelRequested = false,
            updateSequence = 7, submittedMonoNs = 10, startedMonoNs = 20, updatedMonoNs = 30,
            terminalMonoNs = 0, detail = "paused",
        )
        assertFalse(FileTransferState.PAUSED.terminal)
        assertFalse(value.terminal)
        val known = value.copy(state = FileTransferState.COMPLETED, result = FileTransferResult.OK)
        assertFalse(known.terminal)
        assertFalse(known.completed)
        assertTrue(known.copy(terminalMonoNs = -1).terminal) // Opaque nonzero uint64 bits.
        assertTrue(known.copy(terminalMonoNs = 9).completed)
    }

    @Test
    fun streamSnapshotRequiresAnObservedCoreTerminalMarker() {
        val value = StreamSessionSnapshot(
            direction = StreamDirection.PUBLISH, state = StreamState.ENDED, result = StreamResult.OK,
            formatId = 1, frames = 2, bytes = 3, droppedFrames = 4, lastSequence = 5,
            negotiatedMaxFrameBytes = 64, windowBytes = 128, cancelRequested = false,
            updateSequence = 7, submittedMonoNs = 10, startedMonoNs = 20, updatedMonoNs = 30,
            terminalMonoNs = 0, detail = "",
        )
        assertFalse(value.terminal)
        assertFalse(value.completed)
        assertTrue(value.copy(terminalMonoNs = 9).completed)
    }
}
