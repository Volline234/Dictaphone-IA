package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val audioFilePath: String? = null,
    val durationSeconds: Int = 0,
    val rawTranscript: String,
    val language: String = "fr", // "fr" or "en"
    val deliverableType: String = DeliverableType.EMAIL_PRO.name,
    val toneType: String = ToneType.FORMAL.name,
    val generatedContent: String = "",
    val checklistJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val customTemplateName: String? = null
)
