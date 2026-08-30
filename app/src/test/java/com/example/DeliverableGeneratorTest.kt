package com.example

import com.example.data.remote.DeliverableGenerator
import com.example.model.DeliverableType
import com.example.model.ToneType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverableGeneratorTest {

    @Test
    fun `local fallback generates email with proper tone structure`() = runBlocking {
        val transcript = "On valide le lancement pour lundi prochain et Thomas prépare les slides."
        val result = DeliverableGenerator.generateDeliverable(
            rawTranscript = transcript,
            type = DeliverableType.EMAIL_PRO,
            tone = ToneType.FORMAL,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.text.contains("Objet :"))
        assertTrue(result.text.contains("Bonjour,"))
        assertTrue(result.text.contains("Bien cordialement,"))
    }

    @Test
    fun `local fallback generates meeting notes with actionable checklist`() = runBlocking {
        val transcript = "Première décision valider le budget. Deuxième décision recruter un développeur. Action Thomas préparer les contrats."
        val result = DeliverableGenerator.generateDeliverable(
            rawTranscript = transcript,
            type = DeliverableType.MEETING_TASKS,
            tone = ToneType.SYNTHETIC,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.text.contains("COMPTE-RENDU & PLAN D'ACTION"))
        assertTrue(result.text.contains("- [ ]"))
        assertNotNull(result.checklistJson)
        assertTrue(result.checklistJson.startsWith("["))
    }

    @Test
    fun `local fallback generates content creator post with linkedin and video script`() = runBlocking {
        val transcript = "Voici comment doubler sa productivité en organisant ses matinées."
        val result = DeliverableGenerator.generateDeliverable(
            rawTranscript = transcript,
            type = DeliverableType.CONTENT_CREATOR,
            tone = ToneType.DYNAMIC,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.text.contains("POST LINKEDIN"))
        assertTrue(result.text.contains("SCRIPT VIDÉO COURT"))
        assertTrue(result.text.contains("#Productivite"))
    }
}
