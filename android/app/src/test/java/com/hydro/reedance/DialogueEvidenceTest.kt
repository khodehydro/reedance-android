package com.hydro.reedance

import com.hydro.reedance.ui.DialogueTurn
import com.hydro.reedance.ui.dialogueEvidence
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogueEvidenceTest {
    private val track = File("audio.wav")
    private val turns = listOf(
        DialogueTurn(0, "Hello", "", track, id = "t01", startMs = 100, endMs = 300),
        DialogueTurn(1, "Hi", "", track, id = "t02", startMs = 400, endMs = 800),
        DialogueTurn(0, "How are you?", "", track, id = "t03", startMs = 900, endMs = 1200),
    )

    @Test fun rangeFollowsTimelineNotEvidenceOrder() {
        val range = checkNotNull(dialogueEvidence(turns, listOf("t03", "t01"), 0))
        assertEquals(100L, range.startMs)
        assertEquals(1200L, range.endMs)
    }

    @Test fun missingReferencesOrUntimedAudioDoNotProduceAClip() {
        assertNull(dialogueEvidence(turns, listOf("missing", "t01"), 0))
        assertNull(dialogueEvidence(listOf(turns.first().copy(startMs = null)), listOf("t01"), 0))
        assertNull(dialogueEvidence(listOf(turns.first().copy(audio = null)), listOf("t01"), 0))
    }
}
