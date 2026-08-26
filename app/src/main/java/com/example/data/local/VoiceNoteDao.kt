package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.VoiceNote
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getNoteById(id: Long): VoiceNote?

    @Query("SELECT * FROM voice_notes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteNotes(): Flow<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE deliverableType = :type ORDER BY createdAt DESC")
    fun getNotesByType(type: String): Flow<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE title LIKE '%' || :query || '%' OR rawTranscript LIKE '%' || :query || '%' OR generatedContent LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchNotes(query: String): Flow<List<VoiceNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: VoiceNote): Long

    @Update
    suspend fun updateNote(note: VoiceNote)

    @Delete
    suspend fun deleteNote(note: VoiceNote)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM voice_notes")
    suspend fun deleteAllNotes()
}
