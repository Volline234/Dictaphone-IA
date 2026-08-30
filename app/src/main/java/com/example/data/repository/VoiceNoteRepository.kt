package com.example.data.repository

import com.example.data.local.VoiceNoteDao
import com.example.model.VoiceNote
import kotlinx.coroutines.flow.Flow
import java.io.File

class VoiceNoteRepository(private val voiceNoteDao: VoiceNoteDao) {

    val allNotes: Flow<List<VoiceNote>> = voiceNoteDao.getAllNotes()
    val favoriteNotes: Flow<List<VoiceNote>> = voiceNoteDao.getFavoriteNotes()

    fun searchNotes(query: String): Flow<List<VoiceNote>> = voiceNoteDao.searchNotes(query)

    fun getNotesByType(type: String): Flow<List<VoiceNote>> = voiceNoteDao.getNotesByType(type)

    suspend fun getNoteById(id: Long): VoiceNote? = voiceNoteDao.getNoteById(id)

    suspend fun insertNote(note: VoiceNote): Long = voiceNoteDao.insertNote(note)

    suspend fun updateNote(note: VoiceNote) = voiceNoteDao.updateNote(note)

    /**
     * Deletes a note and its associated audio file from disk.
     * The physical .mp4 file is deleted BEFORE the database record
     * to prevent orphaned files consuming device storage.
     */
    suspend fun deleteNote(note: VoiceNote) {
        deleteAudioFile(note.audioFilePath)
        voiceNoteDao.deleteNote(note)
    }

    /**
     * Deletes a note by ID, including its audio file.
     * Fetches the note first to retrieve the audio file path.
     */
    suspend fun deleteNoteById(id: Long) {
        val note = voiceNoteDao.getNoteById(id)
        note?.let { deleteAudioFile(it.audioFilePath) }
        voiceNoteDao.deleteNoteById(id)
    }

    suspend fun deleteAllNotes() = voiceNoteDao.deleteAllNotes()

    /**
     * Safely deletes a physical audio recording file from device storage.
     */
    private fun deleteAudioFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
