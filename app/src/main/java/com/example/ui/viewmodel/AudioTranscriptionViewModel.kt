package com.example.ui.viewmodel

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.audio.AudioRecorderManager
import com.example.data.remote.gemini.GeminiApiService
import com.example.data.remote.gemini.GeminiContent
import com.example.data.remote.gemini.GeminiGenerateContentRequest
import com.example.data.remote.gemini.GeminiGenerationConfig
import com.example.data.remote.gemini.GeminiInlineData
import com.example.data.remote.gemini.GeminiPart
import com.example.data.remote.gemini.GeminiRetrofitClient
import com.example.data.remote.gemini.StructuredAudioTranscription
import com.example.data.repository.VoiceNoteRepository
import com.example.model.DeliverableType
import com.example.model.ToneType
import com.example.model.VoiceNote
import com.example.settings.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * UI State for audio recording and Gemini AI transcription.
 */
sealed interface AudioTranscriptionUiState {
    data object Idle : AudioTranscriptionUiState

    data class Recording(
        val elapsedSeconds: Int = 0,
        val currentAmplitude: Float = 0f,
        val audioBytesCount: Int = 0
    ) : AudioTranscriptionUiState

    data class Transcribing(
        val progressMessage: String = ""
    ) : AudioTranscriptionUiState

    data class Success(
        val transcription: StructuredAudioTranscription,
        val audioDurationSeconds: Int = 0,
        val audioByteCount: Int = 0
    ) : AudioTranscriptionUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : AudioTranscriptionUiState
}

/**
 * ViewModel responsible for capturing audio from the microphone and connecting to
 * the Gemini AI API via Retrofit to transcribe and structure the speech.
 */
class AudioTranscriptionViewModel @JvmOverloads constructor(
    application: Application,
    private val geminiService: GeminiApiService = GeminiRetrofitClient.service,
    val recorderManager: AudioRecorderManager = AudioRecorderManager(application)
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AudioTranscribeVM"
        private const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    private fun appString(resId: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(resId, *args)

    private val _uiState = MutableStateFlow<AudioTranscriptionUiState>(AudioTranscriptionUiState.Idle)
    val uiState: StateFlow<AudioTranscriptionUiState> = _uiState.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(LocaleHelper.effectiveLanguageCode(application))
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Last recorded or provided audio bytes for retry capability
    private var lastAudioBytes: ByteArray? = null
    private var lastMimeType: String = "audio/mp4"
    private var lastCustomInstruction: String? = null
    private var recordedDurationSeconds: Int = 0
    var lastRecordedAudioPath: String? = null
        private set

    init {
        // Observe recorder state to update recording UI state
        viewModelScope.launch {
            recorderManager.isRecording.collect { isRec ->
                if (isRec && _uiState.value !is AudioTranscriptionUiState.Recording) {
                    _uiState.value = AudioTranscriptionUiState.Recording(
                        elapsedSeconds = recorderManager.elapsedSeconds.value,
                        currentAmplitude = recorderManager.currentAmplitude.value
                    )
                }
            }
        }

        viewModelScope.launch {
            recorderManager.elapsedSeconds.collect { seconds ->
                val current = _uiState.value
                if (current is AudioTranscriptionUiState.Recording) {
                    _uiState.value = current.copy(
                        elapsedSeconds = seconds,
                        currentAmplitude = recorderManager.currentAmplitude.value
                    )
                }
            }
        }
    }

    fun setLanguage(lang: String) {
        val prev = _selectedLanguage.value
        _selectedLanguage.value = lang
        if (prev != lang) {
            val bytes = lastAudioBytes
            if (bytes != null && _uiState.value !is AudioTranscriptionUiState.Recording) {
                transcribeAudioBytes(
                    audioBytes = bytes,
                    mimeType = lastMimeType,
                    customInstructions = lastCustomInstruction,
                    language = lang
                )
            }
        }
    }

    /**
     * Starts recording audio from the microphone.
     */
    fun startRecording(): Boolean {
        _uiState.value = AudioTranscriptionUiState.Idle
        val path = recorderManager.startRecording()
        return if (path != null) {
            _uiState.value = AudioTranscriptionUiState.Recording(
                elapsedSeconds = 0,
                currentAmplitude = 0f
            )
            true
        } else {
            _uiState.value = AudioTranscriptionUiState.Error(
                message = appString(R.string.gemini_err_mic_start),
                canRetry = true
            )
            false
        }
    }

    /**
     * Stops the microphone recording, reads the captured audio bytes,
     * and sends them to Gemini AI API via Retrofit for transcription and structuring.
     *
     * @param customInstructions Optional prompt instructions (e.g. format, tone, domain context)
     */
    fun stopAndTranscribe(customInstructions: String? = null) {
        val duration = recorderManager.elapsedSeconds.value
        recordedDurationSeconds = duration
        val recordedFilePath = recorderManager.stopRecording()
        lastRecordedAudioPath = recordedFilePath

        if (recordedFilePath.isNullOrBlank()) {
            _uiState.value = AudioTranscriptionUiState.Error(
                message = appString(R.string.gemini_err_no_file),
                canRetry = false
            )
            return
        }

        val audioFile = File(recordedFilePath)
        if (!audioFile.exists() || audioFile.length() == 0L) {
            _uiState.value = AudioTranscriptionUiState.Error(
                message = appString(R.string.gemini_err_empty_audio),
                canRetry = true
            )
            return
        }

        val audioBytes = audioFile.readBytes()
        transcribeAudioBytes(
            audioBytes = audioBytes,
            mimeType = "audio/mp4",
            customInstructions = customInstructions,
            language = _selectedLanguage.value
        )
    }

    /**
     * Saves the structured transcription as a persistent [VoiceNote] in the local database.
     */
    fun saveToRepository(
        repository: VoiceNoteRepository,
        onSuccess: (Long) -> Unit = {}
    ) {
        val state = _uiState.value
        if (state !is AudioTranscriptionUiState.Success) return

        viewModelScope.launch(Dispatchers.IO) {
            val defaultAssignee = appString(R.string.checklist_default_assignee)
            val defaultDeadline = appString(R.string.checklist_default_deadline)
            val checklistJsonArray = JSONArray()
            state.transcription.actionItems.forEach { item ->
                checklistJsonArray.put(JSONObject().apply {
                    put("text", item.task)
                    put("isDone", false)
                    put("assignee", item.assignee ?: defaultAssignee)
                    put("deadline", item.deadline ?: defaultDeadline)
                })
            }

            val note = VoiceNote(
                title = state.transcription.title,
                audioFilePath = lastRecordedAudioPath,
                durationSeconds = state.audioDurationSeconds,
                rawTranscript = state.transcription.rawTranscription,
                language = state.transcription.languageDetected,
                deliverableType = DeliverableType.MEETING_TASKS.name,
                toneType = ToneType.FORMAL.name,
                generatedContent = state.transcription.toFormattedDisplay(),
                checklistJson = checklistJsonArray.toString(),
                createdAt = System.currentTimeMillis()
            )
            val id = repository.insertNote(note)
            withContext(Dispatchers.Main) {
                onSuccess(id)
            }
        }
    }

    /**
     * Cancels the ongoing recording without transcribing.
     */
    fun cancelRecording() {
        recorderManager.cancelRecording()
        _uiState.value = AudioTranscriptionUiState.Idle
    }

    /**
     * Transcribes raw audio bytes directly into structured text using Gemini AI via Retrofit.
     *
     * @param audioBytes Captured audio bytes from microphone or file
     * @param mimeType Audio MIME type (e.g. "audio/mp4", "audio/wav", "audio/aac")
     * @param customInstructions Optional guidelines for structuring the transcription
     * @param language Expected language ("fr" for French, "en" for English, etc.)
     */
    fun transcribeAudioBytes(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        customInstructions: String? = null,
        language: String = _selectedLanguage.value
    ) {
        lastAudioBytes = audioBytes
        lastMimeType = mimeType
        lastCustomInstruction = customInstructions

        val apiKey = com.example.data.remote.ApiKeyProvider.getGeminiApiKey()

        if (apiKey.isBlank()) {
            _uiState.value = AudioTranscriptionUiState.Error(
                message = appString(R.string.gemini_err_api_key),
                canRetry = false
            )
            return
        }

        _uiState.value = AudioTranscriptionUiState.Transcribing(
            progressMessage = appString(R.string.gemini_sending_audio, audioBytes.size / 1024)
        )

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    executeGeminiTranscription(
                        apiKey = apiKey,
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        customInstructions = customInstructions,
                        language = language
                    )
                }

                _uiState.value = AudioTranscriptionUiState.Success(
                    transcription = result,
                    audioDurationSeconds = recordedDurationSeconds,
                    audioByteCount = audioBytes.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Gemini transcription error", e)
                _uiState.value = AudioTranscriptionUiState.Error(
                    message = e.localizedMessage ?: appString(R.string.gemini_err_generic),
                    canRetry = true
                )
            }
        }
    }

    /**
     * Retries the previous audio transcription request.
     */
    fun retry() {
        val bytes = lastAudioBytes
        if (bytes != null) {
            transcribeAudioBytes(
                audioBytes = bytes,
                mimeType = lastMimeType,
                customInstructions = lastCustomInstruction,
                language = _selectedLanguage.value
            )
        } else {
            _uiState.value = AudioTranscriptionUiState.Idle
        }
    }

    /**
     * Resets the state back to Idle.
     */
    fun resetState() {
        _uiState.value = AudioTranscriptionUiState.Idle
    }

    /**
     * Executes the Retrofit API call with Base64 audio and parses the structured response.
     */
    private suspend fun executeGeminiTranscription(
        apiKey: String,
        audioBytes: ByteArray,
        mimeType: String,
        customInstructions: String?,
        language: String
    ): StructuredAudioTranscription {
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val prompt = buildTranscriptionPrompt(language, customInstructions)

        val request = GeminiGenerateContentRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = base64Audio
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json"
            )
        )

        val response = geminiService.generateContent(
            model = DEFAULT_MODEL,
            apiKey = apiKey,
            request = request
        )

        val rawResponseText = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull { it.text != null }
            ?.text
            ?: throw IllegalStateException(appString(R.string.gemini_err_empty_response))

        return parseStructuredResponse(rawResponseText, language)
    }

    /**
     * Builds the prompt instructing Gemini to transcribe verbatim and output structured JSON.
     */
    private fun buildTranscriptionPrompt(language: String, customInstructions: String?): String {
        val isEnglish = language.startsWith("en", ignoreCase = true)
        val customSection = if (!customInstructions.isNullOrBlank()) {
            if (isEnglish) "\nSpecific directives: $customInstructions\n" else "\nDirectives spécifiques : $customInstructions\n"
        } else ""

        return if (isEnglish) {
            """
                You are an expert audio transcription and voice note structuring system in ENGLISH.
                
                TASK:
                1. Listen to the provided audio recording and accurately transcribe what was spoken.
                2. Provide an explicit, engaging title in ENGLISH (title).
                3. Write a concise, professional executive summary in ENGLISH (summary).
                4. Extract the ordered key points in ENGLISH (keyPoints).
                5. Detect all action items, decisions, and deadlines in ENGLISH (actionItems).
                6. If the audio recording is in French or any other language, TRANSLATE the title, summary, keyPoints, actionItems, and formattedMarkdown entirely into natural, professional ENGLISH.
                7. Write a clean Markdown version with English headings (# Title, ## Summary, ## Key Points, ## Action Items) (formattedMarkdown).
                $customSection
                
                MANDATORY JSON RESPONSE STRUCTURE:
                {
                  "title": "Clear English title",
                  "rawTranscription": "Full verbatim transcription of the audio",
                  "summary": "Clear executive summary in English (2 to 4 sentences)",
                  "keyPoints": [
                    "English key point 1",
                    "English key point 2"
                  ],
                  "actionItems": [
                    {
                      "task": "Task description in English",
                      "assignee": "Person responsible or 'Unassigned'",
                      "deadline": "Deadline or 'TBD'",
                      "priority": "HIGH"
                    }
                  ],
                  "languageDetected": "en",
                  "toneDetected": "professional",
                  "formattedMarkdown": "# Title\n\n## Summary\n...\n\n## Key Points\n- ...\n\n## Action Items\n- [ ] ..."
                }
            """.trimIndent()
        } else {
            """
                Tu es un système expert de transcription audio et de structuration de notes vocales en français.
                
                TÂCHE :
                1. Écoute l'enregistrement audio fourni et retranscris intégralement et fidèlement chaque mot prononcé (rawTranscription).
                2. Déduis un titre clair et pertinent (title).
                3. Rédige un résumé exécutif concis et professionnel (summary).
                4. Extrais la liste ordonnée des points clés abordés (keyPoints).
                5. Détecte toutes les actions, tâches à réaliser, décisions ou échéances mentionnées (actionItems).
                6. Indique la langue détectée (languageDetected: ex 'fr' ou 'en') et le ton (toneDetected: ex 'professionnel', 'décontracté', 'synthétique').
                7. Rédige une version entièrement formatée en Markdown propre avec titres, listes à puces et cases à cocher (formattedMarkdown).
                $customSection
                
                STRUCTURE DU JSON DE RETOUR OBLIGATOIRE :
                {
                  "title": "Titre explicite de la note",
                  "rawTranscription": "Transcription mot à mot complète du fichier audio",
                  "summary": "Synthèse exécutive claire en 2 à 4 phrases",
                  "keyPoints": [
                    "Point clé 1",
                    "Point clé 2"
                  ],
                  "actionItems": [
                    {
                      "task": "Intitulé de la tâche",
                      "assignee": "Personne responsable ou 'Non spécifié'",
                      "deadline": "Date limite ou 'À définir'",
                      "priority": "HIGH" ou "NORMAL" ou "LOW"
                    }
                  ],
                  "languageDetected": "$language",
                  "toneDetected": "professionnel",
                  "formattedMarkdown": "# Titre\n\n## Résumé\n...\n\n## Points Clés\n- ...\n\n## Actions\n- [ ] ..."
                }
            """.trimIndent()
        }
    }

    /**
     * Parses the JSON response into [StructuredAudioTranscription], with fallback handling.
     */
    private fun parseStructuredResponse(
        rawText: String,
        fallbackLanguage: String
    ): StructuredAudioTranscription {
        val cleanJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val adapter = GeminiRetrofitClient.moshi.adapter(StructuredAudioTranscription::class.java)
            val parsed = adapter.fromJson(cleanJson)
            parsed ?: StructuredAudioTranscription(
                title = appString(R.string.gemini_clip_label),
                rawTranscription = cleanJson,
                summary = cleanJson.take(200),
                languageDetected = fallbackLanguage
            )
        } catch (e: Exception) {
            Log.w(TAG, "Moshi parsing failed on response, using fallback extraction", e)
            StructuredAudioTranscription(
                title = appString(R.string.gemini_fallback_title),
                rawTranscription = rawText,
                summary = appString(R.string.gemini_fallback_summary),
                keyPoints = rawText.lines().filter { it.isNotBlank() }.take(5),
                languageDetected = fallbackLanguage,
                formattedMarkdown = rawText
            )
        }
    }
}
