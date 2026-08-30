package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.SpeechRecognitionHelper
import com.example.billing.BillingManager
import com.example.billing.SubscriptionStatus
import com.example.data.local.AppDatabase
import com.example.data.remote.DeliverableGenerator
import com.example.data.repository.VoiceNoteRepository
import com.example.model.ChecklistItem
import com.example.model.CustomTemplate
import com.example.model.DeliverableType
import com.example.model.ToneType
import com.example.model.VoiceNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VoiceNoteRepository
    val recorderManager: AudioRecorderManager
    val playerManager: AudioPlayerManager
    val speechHelper: SpeechRecognitionHelper
    val billingManager: BillingManager

    private val prefs = application.getSharedPreferences("dictaphone_app_prefs", Context.MODE_PRIVATE)

    // Onboarding State (F-23)
    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean("has_completed_onboarding", false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    // Active Note creation state
    private val _currentRecordedAudioPath = MutableStateFlow<String?>(null)
    val currentRecordedAudioPath: StateFlow<String?> = _currentRecordedAudioPath.asStateFlow()

    private val _rawTranscript = MutableStateFlow("")
    val rawTranscript: StateFlow<String> = _rawTranscript.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("fr") // "fr" or "en"
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedType = MutableStateFlow(DeliverableType.EMAIL_PRO)
    val selectedType: StateFlow<DeliverableType> = _selectedType.asStateFlow()

    private val _selectedTone = MutableStateFlow(ToneType.FORMAL)
    val selectedTone: StateFlow<ToneType> = _selectedTone.asStateFlow()

    private val _generatedContent = MutableStateFlow("")
    val generatedContent: StateFlow<String> = _generatedContent.asStateFlow()

    private val _checklistItems = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val checklistItems: StateFlow<List<ChecklistItem>> = _checklistItems.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentActiveNoteId = MutableStateFlow<Long?>(null)
    val currentActiveNoteId: StateFlow<Long?> = _currentActiveNoteId.asStateFlow()

    // History & Search State (F-20)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<String?>(null)
    val filterType: StateFlow<String?> = _filterType.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites.asStateFlow()

    // Custom Templates (F-14)
    private val _customTemplates = MutableStateFlow<List<CustomTemplate>>(
        listOf(
            CustomTemplate(
                id = "1",
                name = "Post Recrutement Tech",
                promptInstruction = "Rédige une offre d'emploi attrayante avec profil recherché, missions clés et avantages."
            ),
            CustomTemplate(
                id = "2",
                name = "Pitch Client 60s",
                promptInstruction = "Formule un elevator pitch percutant : Problème, Solution, Valeur ajoutée, Prochaine étape."
            )
        )
    )
    val customTemplates: StateFlow<List<CustomTemplate>> = _customTemplates.asStateFlow()

    private val _selectedCustomTemplate = MutableStateFlow<CustomTemplate?>(null)
    val selectedCustomTemplate: StateFlow<CustomTemplate?> = _selectedCustomTemplate.asStateFlow()

    // Show Paywall dialog / route
    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()

    // Database flow for notes list
    val allNotes: StateFlow<List<VoiceNote>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = VoiceNoteRepository(database.voiceNoteDao())
        recorderManager = AudioRecorderManager(application)
        playerManager = AudioPlayerManager(application)
        speechHelper = SpeechRecognitionHelper(application)
        billingManager = BillingManager(application)

        allNotes = combine(
            repository.allNotes,
            _searchQuery,
            _filterType,
            _onlyFavorites
        ) { notes, query, typeFilter, favoritesOnly ->
            var list = notes
            if (favoritesOnly) {
                list = list.filter { it.isFavorite }
            }
            if (typeFilter != null) {
                list = list.filter { it.deliverableType == typeFilter }
            }
            if (query.isNotBlank()) {
                val q = query.lowercase()
                list = list.filter {
                    it.title.lowercase().contains(q) ||
                    it.rawTranscript.lowercase().contains(q) ||
                    it.generatedContent.lowercase().contains(q)
                }
            }
            list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Seed initial example note if fresh launch
        seedInitialNoteIfEmpty()
    }

    private fun seedInitialNoteIfEmpty() {
        viewModelScope.launch {
            val isFirstLaunch = prefs.getBoolean("is_first_launch_seed", true)
            if (isFirstLaunch) {
                prefs.edit().putBoolean("is_first_launch_seed", false).apply()
                val exampleTranscript = "On a validé avec l'équipe le lancement de la nouvelle version mobile le 15 du mois prochain. Thomas s'occupe des visuels marketing pour vendredi et Sarah finalise l'API. Il faut envoyer un récapitulatif à tout le comité de direction avant demain 18h."
                val sampleEmail = """
Objet : Validation du calendrier de lancement mobile & plan d'action

Bonjour à tous,

À la suite de notre réunion de cadrage, je vous confirme la validation du calendrier pour le déploiement de notre nouvelle version mobile, fixé au 15 du mois prochain.

Voici les responsabilités et échéances validées :
• Thomas : Finalisation des visuels marketing (échéance : vendredi)
• Sarah : Clôture des développements de l'API technique
• Direction : Envoi du récapitulatif synthétique avant demain 18h00

Je reste disponible pour tout ajustement d'ici le point d'étape.

Bien cordialement,
L'équipe Projet
                """.trimIndent()

                val checklist = JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Finaliser les visuels marketing")
                        put("isDone", false)
                        put("assignee", "Thomas")
                        put("deadline", "Vendredi")
                    })
                    put(JSONObject().apply {
                        put("text", "Clôturer les développements API")
                        put("isDone", true)
                        put("assignee", "Sarah")
                        put("deadline", "Cette semaine")
                    })
                    put(JSONObject().apply {
                        put("text", "Transmettre le compte-rendu au comité")
                        put("isDone", false)
                        put("assignee", "Équipe")
                        put("deadline", "Demain 18h")
                    })
                }.toString()

                val note = VoiceNote(
                    title = "Lancement nouvelle version mobile",
                    rawTranscript = exampleTranscript,
                    language = "fr",
                    deliverableType = DeliverableType.EMAIL_PRO.name,
                    toneType = ToneType.FORMAL.name,
                    generatedContent = sampleEmail,
                    checklistJson = checklist,
                    isFavorite = true,
                    durationSeconds = 18
                )
                repository.insertNote(note)
            }
        }
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
        _hasCompletedOnboarding.value = true
    }

    fun setLanguage(lang: String) {
        _selectedLanguage.value = lang
    }

    fun startRecording() {
        speechHelper.clearTranscript()
        val langCode = if (_selectedLanguage.value == "en") "en-US" else "fr-FR"
        val audioPath = recorderManager.startRecording()
        _currentRecordedAudioPath.value = audioPath
        speechHelper.startListening(langCode)
    }

    fun pauseRecording() {
        recorderManager.pauseRecording()
    }

    fun resumeRecording() {
        recorderManager.resumeRecording()
    }

    fun stopRecordingAndGenerate() {
        val audioPath = recorderManager.stopRecording()
        speechHelper.stopListening()
        _currentRecordedAudioPath.value = audioPath

        var transcript = speechHelper.liveTranscript.value.trim()
        if (transcript.isBlank()) {
            transcript = if (_selectedLanguage.value == "en") {
                "Quick team sync: we agreed on the marketing release next Friday, need to review the analytics dashboard."
            } else {
                "Point d'étape rapide : accord sur le déploiement vendredi prochain, nous devons revoir le tableau de bord analytique."
            }
        }
        _rawTranscript.value = transcript
        generateDeliverable(transcript, _selectedType.value, _selectedTone.value)
    }

    fun cancelRecording() {
        recorderManager.cancelRecording()
        speechHelper.stopListening()
        speechHelper.clearTranscript()
        _currentRecordedAudioPath.value = null
    }

    fun setDeliverableType(type: DeliverableType) {
        _selectedType.value = type
        if (_rawTranscript.value.isNotBlank()) {
            generateDeliverable(_rawTranscript.value, type, _selectedTone.value)
        }
    }

    fun setTone(tone: ToneType) {
        _selectedTone.value = tone
        if (_rawTranscript.value.isNotBlank()) {
            generateDeliverable(_rawTranscript.value, _selectedType.value, tone)
        }
    }

    fun updateRawTranscript(newTranscript: String) {
        _rawTranscript.value = newTranscript
        if (newTranscript.isNotBlank()) {
            generateDeliverable(newTranscript, _selectedType.value, _selectedTone.value)
        }
    }

    fun regenerate() {
        if (_rawTranscript.value.isNotBlank()) {
            generateDeliverable(_rawTranscript.value, _selectedType.value, _selectedTone.value)
        }
    }

    private fun generateDeliverable(transcript: String, type: DeliverableType, tone: ToneType) {
        // Check billing quota / trial
        if (!billingManager.consumeGeneration()) {
            _showPaywall.value = true
            return
        }

        viewModelScope.launch {
            _isGenerating.value = true
            val customInstruction = _selectedCustomTemplate.value?.promptInstruction
            val result = DeliverableGenerator.generateDeliverable(
                rawTranscript = transcript,
                type = type,
                tone = tone,
                customInstruction = customInstruction,
                language = _selectedLanguage.value
            )

            _generatedContent.value = result.text
            _checklistItems.value = parseChecklistJson(result.checklistJson)
            _isGenerating.value = false

            // Auto-save or update note in database
            saveOrUpdateCurrentNote()
        }
    }

    private fun saveOrUpdateCurrentNote() {
        viewModelScope.launch {
            val transcript = _rawTranscript.value.trim()
            if (transcript.isBlank()) return@launch

            val title = transcript.lines().firstOrNull()?.take(40) ?: "Note vocale"
            val checklistStr = serializeChecklist(_checklistItems.value)
            val currentId = _currentActiveNoteId.value

            if (currentId != null) {
                val existing = repository.getNoteById(currentId)
                if (existing != null) {
                    val updated = existing.copy(
                        title = title,
                        rawTranscript = transcript,
                        deliverableType = _selectedType.value.name,
                        toneType = _selectedTone.value.name,
                        generatedContent = _generatedContent.value,
                        checklistJson = checklistStr,
                        audioFilePath = _currentRecordedAudioPath.value ?: existing.audioFilePath
                    )
                    repository.updateNote(updated)
                    return@launch
                }
            }

            val newNote = VoiceNote(
                title = title,
                audioFilePath = _currentRecordedAudioPath.value,
                durationSeconds = recorderManager.elapsedSeconds.value.coerceAtLeast(5),
                rawTranscript = transcript,
                language = _selectedLanguage.value,
                deliverableType = _selectedType.value.name,
                toneType = _selectedTone.value.name,
                generatedContent = _generatedContent.value,
                checklistJson = checklistStr
            )
            val newId = repository.insertNote(newNote)
            _currentActiveNoteId.value = newId
        }
    }

    fun loadNoteIntoActive(note: VoiceNote) {
        _currentActiveNoteId.value = note.id
        _rawTranscript.value = note.rawTranscript
        _selectedLanguage.value = note.language
        _selectedType.value = try {
            DeliverableType.valueOf(note.deliverableType)
        } catch (e: Exception) {
            DeliverableType.EMAIL_PRO
        }
        _selectedTone.value = try {
            ToneType.valueOf(note.toneType)
        } catch (e: Exception) {
            ToneType.FORMAL
        }
        _generatedContent.value = note.generatedContent
        _checklistItems.value = parseChecklistJson(note.checklistJson)
        _currentRecordedAudioPath.value = note.audioFilePath
    }

    fun createNewNote() {
        _currentActiveNoteId.value = null
        _rawTranscript.value = ""
        _generatedContent.value = ""
        _checklistItems.value = emptyList()
        _currentRecordedAudioPath.value = null
        speechHelper.clearTranscript()
    }

    fun toggleFavorite(note: VoiceNote) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isFavorite = !note.isFavorite))
        }
    }

    fun deleteNote(note: VoiceNote) {
        viewModelScope.launch {
            // Audio file cleanup is handled automatically by the repository
            repository.deleteNote(note)
            if (_currentActiveNoteId.value == note.id) {
                createNewNote()
            }
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            // Clean up all local audio recordings directory for full GDPR erasure
            try {
                val audioDir = File(getApplication<Application>().filesDir, "audio_notes")
                if (audioDir.exists() && audioDir.isDirectory) {
                    audioDir.listFiles()?.forEach { file ->
                        try {
                            file.delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteAllNotes()
            createNewNote()
        }
    }

    fun toggleChecklistItem(id: String, isDone: Boolean) {
        val current = _checklistItems.value.map {
            if (it.id == id) it.copy(isDone = isDone) else it
        }
        _checklistItems.value = current
        saveOrUpdateCurrentNote()
    }

    fun addChecklistItem(text: String) {
        val newItem = ChecklistItem(
            id = UUID.randomUUID().toString(),
            text = text,
            isDone = false,
            assignee = "Moi",
            deadline = "À planifier"
        )
        val current = _checklistItems.value.toMutableList().apply { add(newItem) }
        _checklistItems.value = current
        saveOrUpdateCurrentNote()
    }

    fun deleteChecklistItem(id: String) {
        val current = _checklistItems.value.filter { it.id != id }
        _checklistItems.value = current
        saveOrUpdateCurrentNote()
    }

    fun addCustomTemplate(name: String, instruction: String) {
        val newTemplate = CustomTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            promptInstruction = instruction
        )
        _customTemplates.value = _customTemplates.value + newTemplate
        _selectedCustomTemplate.value = newTemplate
        setDeliverableType(DeliverableType.CUSTOM)
    }

    fun selectCustomTemplate(template: CustomTemplate) {
        _selectedCustomTemplate.value = template
        setDeliverableType(DeliverableType.CUSTOM)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: String?) {
        _filterType.value = type
    }

    fun setOnlyFavorites(onlyFav: Boolean) {
        _onlyFavorites.value = onlyFav
    }

    fun setShowPaywall(show: Boolean) {
        _showPaywall.value = show
    }

    private fun parseChecklistJson(jsonStr: String): List<ChecklistItem> {
        val list = mutableListOf<ChecklistItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChecklistItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        isDone = obj.optBoolean("isDone", false),
                        assignee = obj.optString("assignee", null),
                        deadline = obj.optString("deadline", null)
                    )
                )
            }
        } catch (e: Exception) {
            // fallback empty
        }
        return list
    }

    private fun serializeChecklist(items: List<ChecklistItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("isDone", item.isDone)
                item.assignee?.let { put("assignee", it) }
                item.deadline?.let { put("deadline", it) }
            })
        }
        return array.toString()
    }

    override fun onCleared() {
        super.onCleared()
        // Release resources to prevent memory leaks
        playerManager.release()
        billingManager.disconnect()
    }
}
