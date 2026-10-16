package com.example.data.remote.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Structured output model representing transcribed and analyzed audio.
 */
@JsonClass(generateAdapter = true)
data class StructuredAudioTranscription(
    @Json(name = "title") val title: String = "Transcription Vocale",
    @Json(name = "rawTranscription") val rawTranscription: String = "",
    @Json(name = "summary") val summary: String = "",
    @Json(name = "keyPoints") val keyPoints: List<String> = emptyList(),
    @Json(name = "actionItems") val actionItems: List<StructuredActionItem> = emptyList(),
    @Json(name = "languageDetected") val languageDetected: String = "fr",
    @Json(name = "toneDetected") val toneDetected: String? = null,
    @Json(name = "formattedMarkdown") val formattedMarkdown: String = ""
) {
    /**
     * Returns a formatted Markdown string, using [formattedMarkdown] if available,
     * or generating a structured representation from the component fields.
     */
    fun toFormattedDisplay(): String {
        if (formattedMarkdown.isNotBlank()) return formattedMarkdown

        val isEnglish = languageDetected.startsWith("en", ignoreCase = true)
        val sb = StringBuilder()
        sb.append("# $title\n\n")

        if (summary.isNotBlank()) {
            val heading = if (isEnglish) "## 📌 Summary" else "## 📌 Résumé"
            sb.append("$heading\n$summary\n\n")
        }

        if (keyPoints.isNotEmpty()) {
            val heading = if (isEnglish) "## 💡 Key Points" else "## 💡 Points Clés"
            sb.append("$heading\n")
            keyPoints.forEach { point ->
                sb.append("• $point\n")
            }
            sb.append("\n")
        }

        if (actionItems.isNotEmpty()) {
            val heading = if (isEnglish) "## ✅ Action Items" else "## ✅ Actions & Tâches"
            val deadlineLabel = if (isEnglish) "Deadline" else "Échéance"
            sb.append("$heading\n")
            actionItems.forEach { item ->
                val assignee = item.assignee?.let { " (@$it)" } ?: ""
                val deadline = item.deadline?.let { " [$deadlineLabel: $it]" } ?: ""
                sb.append("- [ ] ${item.task}$assignee$deadline\n")
            }
            sb.append("\n")
        }

        if (rawTranscription.isNotBlank()) {
            val heading = if (isEnglish) "## 🎙️ Raw Transcription" else "## 🎙️ Transcription Brute"
            sb.append("$heading\n")
            sb.append("> $rawTranscription\n")
        }

        return sb.toString()
    }
}

@JsonClass(generateAdapter = true)
data class StructuredActionItem(
    @Json(name = "task") val task: String = "",
    @Json(name = "assignee") val assignee: String? = null,
    @Json(name = "deadline") val deadline: String? = null,
    @Json(name = "priority") val priority: String? = "NORMAL"
)
