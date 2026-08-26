package com.example.data.repository

import com.example.data.local.VoiceNoteDao
import com.example.model.VoiceNote
import kotlinx.coroutines.flow.Flow

class VoiceNoteRepository(private val voiceNoteDao: VoiceNoteDao) {

    val allNotes: Flow<List<VoiceNote>> = voiceNoteDao.getAllNotes()
    val favoriteNotes: Flow<List<VoiceNote>> = voiceNoteDao.getFavoriteNotes()

    fun searchNotes(query: String): Flow<List<VoiceNote>> = voiceNoteDao.searchNotes(query)

    fun getNotesByType(type: String): Flow<List<VoiceNote>> = voiceNoteDao.getNotesByType(type)

    suspend fun getNoteById(id: Long): VoiceNote? = voiceNoteDao.getNoteById(id)

    suspend fun insertNote(note: VoiceNote): Long = voiceNoteDao.insertNote(note)

    suspend fun updateNote(note: VoiceNote) = voiceNoteDao.updateNote(note)

    suspend fun deleteNote(note: VoiceNote) = voiceNoteDao.deleteNote(note)

    suspend fun deleteNoteById(id: Long) = voiceNoteDao.deleteNoteById(id)

    suspend fun deleteAllNotes() = voiceNoteDao.deleteAllNotes()
}
