package com.example

import com.example.model.ChecklistItem
import com.example.model.DeliverableType
import com.example.model.ToneType
import com.example.model.VoiceNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsAndEnumsTest {

    @Test
    fun `checklistItem immutability and copy behavior`() {
        val item = ChecklistItem(
            id = "task-1",
            text = "Finaliser l'API",
            isDone = false,
            assignee = "Sarah",
            deadline = "Vendredi"
        )

        assertFalse(item.isDone)
        assertEquals("Sarah", item.assignee)

        // Verify copy returns a new instance with updated state
        val completedItem = item.copy(isDone = true)
        assertTrue(completedItem.isDone)
        assertFalse(item.isDone) // Original remains unchanged (immutability)
    }

    @Test
    fun `deliverableType enum contains all expected formats`() {
        val types = DeliverableType.entries
        assertEquals(4, types.size)
        assertTrue(types.contains(DeliverableType.EMAIL_PRO))
        assertTrue(types.contains(DeliverableType.MEETING_TASKS))
        assertTrue(types.contains(DeliverableType.CONTENT_CREATOR))
        assertTrue(types.contains(DeliverableType.CUSTOM))
    }

    @Test
    fun `toneType promptModifier provides correct instructions`() {
        ToneType.entries.forEach { tone ->
            assertNotNull(tone.label)
            assertNotNull(tone.promptModifier)
            assertTrue(tone.promptModifier.isNotBlank())
        }
        assertEquals("Utilise un ton formel, professionnel, élégant et rigoureux.", ToneType.FORMAL.promptModifier)
    }

    @Test
    fun `voiceNote model default values and properties`() {
        val note = VoiceNote(
            title = "Réunion lancement",
            rawTranscript = "Transcription test",
            language = "fr",
            deliverableType = DeliverableType.EMAIL_PRO.name,
            toneType = ToneType.FORMAL.name,
            generatedContent = "Email généré",
            durationSeconds = 25
        )

        assertEquals("Réunion lancement", note.title)
        assertEquals(0L, note.id)
        assertFalse(note.isFavorite)
        assertEquals("[]", note.checklistJson)
        assertEquals(25, note.durationSeconds)
    }
}
