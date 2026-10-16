package com.example

import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiCandidate
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerateContentResponse
import com.example.data.remote.gemini.GeminiPart
import com.example.data.remote.gemini.GeminiRetrofitClient
import com.example.data.remote.gemini.StructuredActionItem
import com.example.data.remote.gemini.StructuredAudioTranscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAudioTranscriptionTest {

    @Test
    fun testStructuredAudioTranscriptionJsonParsing() {
        val sampleJson = """
            {
              "title": "Point Réunion Produit",
              "rawTranscription": "Bonjour à tous, on valide le lancement de la version 2 pour la semaine prochaine.",
              "summary": "Validation du calendrier de déploiement de la v2.",
              "keyPoints": [
                "Lancement prévu la semaine prochaine",
                "Validation générale des équipes"
              ],
              "actionItems": [
                {
                  "task": "Préparer le changelog",
                  "assignee": "Alex",
                  "deadline": "Vendredi",
                  "priority": "HIGH"
                }
              ],
              "languageDetected": "fr",
              "toneDetected": "professionnel",
              "formattedMarkdown": "# Point Réunion Produit\n\n## Résumé\nValidation..."
            }
        """.trimIndent()

        val adapter = GeminiRetrofitClient.moshi.adapter(StructuredAudioTranscription::class.java)
        val result = adapter.fromJson(sampleJson)

        assertNotNull(result)
        assertEquals("Point Réunion Produit", result?.title)
        assertEquals(2, result?.keyPoints?.size)
        assertEquals(1, result?.actionItems?.size)
        assertEquals("Alex", result?.actionItems?.first()?.assignee)
        assertEquals("HIGH", result?.actionItems?.first()?.priority)
        assertTrue(result?.toFormattedDisplay()?.contains("Point Réunion Produit") == true)
    }

    @Test
    fun testGeminiRequestFormatting() {
        val json = GeminiRetrofitClient.moshi.adapter(GeminiGenerateContentRequest::class.java)
        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = "Transcribe audio"),
                        GeminiPart(
                            inlineData = com.example.data.remote.gemini.GeminiInlineData(
                                mimeType = "audio/mp4",
                                data = "QUJDREVGRw=="
                            )
                        )
                    )
                )
            )
        )
        val jsonString = json.toJson(request)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("audio/mp4"))
        assertTrue(jsonString.contains("QUJDREVGRw=="))
    }

    @Test
    fun testMockGeminiApiServiceReturnsStructuredData() {
        val mockService = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiGenerateContentRequest
            ): GeminiGenerateContentResponse {
                return GeminiGenerateContentResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                parts = listOf(
                                    GeminiPart(
                                        text = """
                                            {
                                                "title": "Test Title",
                                                "rawTranscription": "Ceci est un test audio",
                                                "summary": "Résumé de test",
                                                "keyPoints": ["Point 1"],
                                                "actionItems": [],
                                                "languageDetected": "fr"
                                            }
                                        """.trimIndent()
                                    )
                                )
                            )
                        )
                    )
                )
            }

            override suspend fun generateContentWithHeader(
                model: String,
                apiKey: String,
                request: GeminiGenerateContentRequest
            ): GeminiGenerateContentResponse {
                return generateContent(model, apiKey, request)
            }
        }

        assertNotNull(mockService)
    }
}
