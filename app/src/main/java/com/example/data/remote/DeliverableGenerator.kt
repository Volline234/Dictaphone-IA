package com.example.data.remote

import android.os.Build
import android.util.Base64
import com.example.model.DeliverableType
import com.example.model.ToneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeliverableGenerator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun isRobolectricUnitTest(): Boolean {
        return try {
            Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        } catch (e: Throwable) {
            true
        }
    }

    /**
     * Transcribes raw recorded audio bytes directly via Gemini 2.5 Flash multimodal API
     * and generates the requested structured deliverable in one pass.
     */
    suspend fun generateDeliverableFromAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String? = null,
        language: String = "fr"
    ): GenerationResult = withContext(Dispatchers.IO) {
        val isEnglish = language.startsWith("en", ignoreCase = true)
        val apiKey = ApiKeyProvider.getGeminiApiKey()

        if (apiKey.isBlank()) {
            val errMsg = if (isEnglish) {
                "⚠️ Gemini API key is not configured."
            } else {
                "⚠️ Clé API Gemini non configurée."
            }
            return@withContext GenerationResult(
                text = errMsg,
                checklistJson = "[]",
                rawTranscript = "",
                isAiPowered = false,
                errorMessage = errMsg
            )
        }

        try {
            val base64Audio = try {
                Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            } catch (e: Throwable) {
                java.util.Base64.getEncoder().encodeToString(audioBytes)
            }
            val prompt = buildAudioPrompt(type, tone, customInstruction, language)
            val responseText = callGeminiMultimodalRest(
                apiKey = apiKey,
                prompt = prompt,
                base64Audio = base64Audio,
                mimeType = mimeType
            )
            if (responseText.isNotBlank()) {
                return@withContext parseAudioDeliverableOutput(responseText, type, isEnglish)
            }
        } catch (e: Exception) {
            val errMsg = if (isEnglish) {
                "⚠️ Unable to transcribe the voice recording (${e.localizedMessage ?: "Network error"}). Please check your internet connection and tap Regenerate."
            } else {
                "⚠️ Impossible de transcrire l'enregistrement vocal (${e.localizedMessage ?: "Erreur réseau"}). Vérifiez votre connexion Internet puis appuyez sur Régénérer."
            }
            return@withContext GenerationResult(
                text = errMsg,
                checklistJson = "[]",
                rawTranscript = "",
                isAiPowered = false,
                errorMessage = errMsg
            )
        }

        val emptyMsg = if (isEnglish) {
            "⚠️ No speech was detected in the recording. Please record again speaking clearly near the microphone."
        } else {
            "⚠️ Aucune parole n'a été détectée dans l'enregistrement. Veuillez réessayer en parlant clairement près du microphone."
        }
        return@withContext GenerationResult(
            text = emptyMsg,
            checklistJson = "[]",
            rawTranscript = "",
            isAiPowered = false,
            errorMessage = emptyMsg
        )
    }

    suspend fun generateDeliverable(
        rawTranscript: String,
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String? = null,
        language: String = "fr"
    ): GenerationResult = withContext(Dispatchers.IO) {
        val apiKey = ApiKeyProvider.getGeminiApiKey()
        val isEnglish = language.startsWith("en", ignoreCase = true)
        var lastError: String? = null

        if (!isRobolectricUnitTest() && apiKey.isNotBlank()) {
            try {
                val prompt = buildPrompt(rawTranscript, type, tone, customInstruction, language)
                val responseText = callGeminiRest(apiKey, prompt)
                if (responseText.isNotBlank()) {
                    val parsed = parseDeliverableOutput(responseText, type, isEnglish)
                    return@withContext parsed.copy(
                        rawTranscript = rawTranscript,
                        isAiPowered = true,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: if (isEnglish) "Network / Gemini API error" else "Erreur réseau / API Gemini"
            }
        } else {
            lastError = if (isEnglish) "Gemini API key not configured (local engine active)" else "Clé API Gemini non configurée (moteur local actif)"
        }

        // Fallback engine: transforms existing text transcript with structural templates
        val fallback = generateLocalStructuredDeliverable(rawTranscript, type, tone, customInstruction, language)
        return@withContext fallback.copy(
            rawTranscript = rawTranscript,
            isAiPowered = false,
            errorMessage = lastError
        )
    }

    private fun buildAudioPrompt(
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String?,
        language: String
    ): String {
        val isEnglish = language.startsWith("en", ignoreCase = true)
        val toneInstruction = tone.getPromptModifier(isEnglish)
        val formatRequirement = buildFormatRequirement(type, customInstruction, isEnglish)

        return if (isEnglish) {
            """
                You are the expert productivity assistant for the application 'Dictaphone IA'.
                Listen carefully to the attached user voice recording.
                
                STRICT RULES:
                1. Transcribe ONLY what the user actually says in the audio recording. NEVER invent fictional meetings, fake names, or pre-filled topics that are not in the audio.
                2. Target Language for the deliverable: ENGLISH.
                3. Tone: $toneInstruction
                4. Output format MUST strictly use these exact delimiters:
                
                ---RAW_TRANSCRIPT---
                [Write the exact verbatim transcription of what the speaker said in the audio]
                ---DELIVERABLE---
                [Write the structured deliverable following the instructions below]
                
                $formatRequirement
            """.trimIndent()
        } else {
            """
                Tu es l'assistant de productivité expert de l'application 'Dictaphone IA'.
                Écoute attentivement l'enregistrement vocal joint de l'utilisateur.
                
                RÈGLES STRICTES :
                1. Transcris et traite UNIQUEMENT ce que l'utilisateur dit réellement dans l'enregistrement audio. N'invente JAMAIS de réunions fictives, de faux prénoms ou de sujets pré-remplis absents de l'audio.
                2. Langue cible du livrable : FRANÇAIS.
                3. Tonalité : $toneInstruction
                4. Le format de sortie DOIT obligatoirement utiliser ces délimiteurs exacts :
                
                ---RAW_TRANSCRIPT---
                [Écris ici la transcription exacte et fidèle des paroles prononcées dans l'audio]
                ---DELIVERABLE---
                [Écris ici le livrable structuré en suivant les consignes ci-dessous]
                
                $formatRequirement
            """.trimIndent()
        }
    }

    private fun buildFormatRequirement(
        type: DeliverableType,
        customInstruction: String?,
        isEnglish: Boolean
    ): String {
        return if (isEnglish) {
            when (type) {
                DeliverableType.EMAIL_PRO -> """
                    Transform this voice recording into a professional business EMAIL in ENGLISH.
                    Mandatory structure:
                    - Subject: [Clear, compelling and explicit subject line in English]
                    - Professional greeting (e.g., Dear Team, / Hello,)
                    - Email body: clear, well-structured into concise paragraphs based strictly on what was said.
                    - Professional sign-off (e.g., Best regards, / Sincerely,)
                """.trimIndent()

                DeliverableType.MEETING_TASKS -> """
                    Transform this voice recording into a STRUCTURED SUMMARY & ACTION PLAN in ENGLISH.
                    Mandatory structure:
                    1. EXECUTIVE SUMMARY (2-3 key sentences in English)
                    2. KEY POINTS / DECISIONS (bulleted list in English)
                    3. ACTION ITEMS & CHECKLIST (clear task list formatted as '- [ ] Action: Owner | Deadline' in English)
                    
                    After the main text, add a special section delimited by `---CHECKLIST_JSON---` containing a valid JSON array of objects with keys "text" (task title in English), "assignee" (person or "Team"), and "deadline" (if mentioned or "TBD").
                """.trimIndent()

                DeliverableType.CONTENT_CREATOR -> """
                    Transform this voice recording into READY-TO-PUBLISH EDITORIAL CONTENT in ENGLISH.
                    Provide two distinct options:
                    
                    === OPTION 1: LINKEDIN POST (Max 1300 characters in English) ===
                    - Compelling hook (1-2 lines creating curiosity)
                    - Clean body (spacious line breaks, bullet points)
                    - Concrete insight or actionable lesson
                    - Call to action (engaging closing question)
                    - 3 to 5 strategic hashtags in English
                    
                    === OPTION 2: SHORT VIDEO SCRIPT (Reels / TikTok / Shorts in English) ===
                    - 0-3s [Visual & Verbal Hook]
                    - 4-30s [Core Insight / Development]
                    - 31-45s [Punchy ending & Clear Call to Action]
                """.trimIndent()

                DeliverableType.CUSTOM -> """
                    Apply the following custom instruction to this voice recording in ENGLISH:
                    Instruction: ${customInstruction ?: "Synthesize and structure this content into a professional executive deliverable in English."}
                """.trimIndent()
            }
        } else {
            when (type) {
                DeliverableType.EMAIL_PRO -> """
                    Transforme cet enregistrement vocal en un E-MAIL PROFESSIONNEL parfait en français.
                    Structure obligatoire :
                    - Objet : [Un objet clair, percutant et explicite]
                    - Formule de politesse d'ouverture (ex: Bonjour,)
                    - Corps de l'email : clair, bien découpé en paragraphes concis basés strictement sur ce qui a été dit.
                    - Formule de politesse de clôture (ex: Bien cordialement,)
                """.trimIndent()

                DeliverableType.MEETING_TASKS -> """
                    Transforme cet enregistrement vocal en un COMPTE-RENDU STRUCTURÉ AVEC PLAN D'ACTION en français.
                    Structure obligatoire :
                    1. RÉSUMÉ EXÉCUTIF (2-3 phrases clés)
                    2. POINTS CLÉS & DÉCISIONS (liste à puces)
                    3. PLAN D'ACTIONS & TÂCHES (liste de tâches claires sous la forme '- [ ] Action: Responsable | Échéance')
                    
                    Après le texte principal, ajoute une section spéciale délimitée par `---CHECKLIST_JSON---` contenant un tableau JSON d'objets avec les champs "text" (intitulé de la tâche), "assignee" (personne ou "Équipe"), et "deadline" (si mentionnée ou "À définir").
                """.trimIndent()

                DeliverableType.CONTENT_CREATOR -> """
                    Transforme cet enregistrement vocal en un CONTENU ÉDITORIAL PRÊT À PUBLIER en français.
                    Fournis deux variantes bien distinctes :
                    
                    === OPTION 1 : POST LINKEDIN (Max 1300 caractères) ===
                    - Accroche percutante (1-2 lignes qui créent la curiosité)
                    - Corps aéré (sauts de lignes réguliers, bullet points)
                    - Enseignement ou perspective concrète
                    - Appel à l'action (CTA engageant posant une question)
                    - 3 à 5 Hashtags stratégiques
                    
                    === OPTION 2 : SCRIPT VIDÉO COURT (Reels / TikTok / Shorts) ===
                    - 0-3s [Accroche visuelle & verbale]
                    - 4-30s [Développement / Storytelling]
                    - 31-45s [Chute & Appel à l'action clair]
                """.trimIndent()

                DeliverableType.CUSTOM -> """
                    Applique la consigne personnalisée suivante à cet enregistrement vocal en français :
                    Consigne : ${customInstruction ?: "Synthétise et structure ce texte de manière professionnelle."}
                """.trimIndent()
            }
        }
    }

    private fun buildPrompt(
        rawTranscript: String,
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String?,
        language: String
    ): String {
        val isEnglish = language.startsWith("en", ignoreCase = true)
        val toneInstruction = tone.getPromptModifier(isEnglish)
        val formatRequirement = buildFormatRequirement(type, customInstruction, isEnglish)

        return if (isEnglish) {
            """
                You are the expert productivity assistant for the application 'Dictaphone IA - From Voice to Workflow'.
                Your mission is to convert a spontaneous, conversational, or messy voice capture into a polished, high-value professional deliverable.
                
                CRITICAL LANGUAGE DIRECTIVE:
                - Target Language: ENGLISH.
                - You MUST output the entire deliverable in ENGLISH.
                - If the raw voice transcription below is in French or any other language, you MUST TRANSLATE all ideas, facts, and intentions faithfully into fluent, natural, professional ENGLISH.
                - All headings, titles, bullet points, greetings, closings, and checklist items MUST BE IN ENGLISH.
                - Tone: $toneInstruction
                - Remove verbal filler, hesitations, stuttering, repetitions, and informal spoken glitches.
                - Accurately preserve all substantive facts, figures, names, and decisions.
                
                $formatRequirement
                
                RAW VOICE TRANSCRIPTION:
                \"\"\"
                $rawTranscript
                \"\"\"
            """.trimIndent()
        } else {
            """
                Tu es l'assistant de productivité expert de l'application 'Dictaphone IA - De la voix au flux de travail'.
                Ta mission est de convertir une capture vocale spontanée, potentiellement hésitante ou désordonnée, en un livrable professionnel soigné et directement exploitable.
                
                Consignes éditoriales :
                - Langue cible : en français
                - Tonalité : $toneInstruction
                - Supprime les tics de langage, hésitations, redondances et formulations orales maladroites.
                - Restitue fidèlement toutes les idées et décisions clés.
                
                $formatRequirement
                
                TRANSCRIPTION VOCALE BRUTE :
                \"\"\"
                $rawTranscript
                \"\"\"
            """.trimIndent()
        }
    }

    private fun callGeminiMultimodalRest(
        apiKey: String,
        prompt: String,
        base64Audio: String,
        mimeType: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", base64Audio)
                            })
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: $bodyStr")
        }

        val json = JSONObject(bodyStr)
        val candidates = json.optJSONArray("candidates") ?: return ""
        val candidate = candidates.optJSONObject(0) ?: return ""
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i)
            val txt = part?.optString("text", "") ?: ""
            if (txt.isNotEmpty()) sb.append(txt)
        }
        return sb.toString()
    }

    private fun callGeminiRest(apiKey: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: $bodyStr")
        }

        val json = JSONObject(bodyStr)
        val candidates = json.optJSONArray("candidates") ?: return ""
        val candidate = candidates.optJSONObject(0) ?: return ""
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val part = parts.optJSONObject(0) ?: return ""
        return part.optString("text", "")
    }

    private fun parseAudioDeliverableOutput(
        output: String,
        type: DeliverableType,
        isEnglish: Boolean
    ): GenerationResult {
        var extractedTranscript = ""
        var deliverableBody = output.trim()

        if (deliverableBody.contains("---DELIVERABLE---")) {
            val sections = deliverableBody.split("---DELIVERABLE---", limit = 2)
            extractedTranscript = sections[0]
                .replace("---RAW_TRANSCRIPT---", "")
                .trim()
            deliverableBody = sections[1].trim()
        } else if (deliverableBody.contains("---RAW_TRANSCRIPT---")) {
            val cleaned = deliverableBody.replace("---RAW_TRANSCRIPT---", "").trim()
            extractedTranscript = cleaned.lines().firstOrNull()?.trim() ?: cleaned.take(200)
            deliverableBody = cleaned
        }

        val parsed = parseDeliverableOutput(deliverableBody, type, isEnglish)
        val finalTranscript = if (extractedTranscript.isNotBlank()) {
            extractedTranscript
        } else {
            parsed.text.lines().firstOrNull { it.isNotBlank() }?.take(120) ?: ""
        }

        return parsed.copy(
            rawTranscript = finalTranscript,
            isAiPowered = true,
            errorMessage = null
        )
    }

    private fun parseDeliverableOutput(output: String, type: DeliverableType, isEnglish: Boolean = false): GenerationResult {
        if (type == DeliverableType.MEETING_TASKS && output.contains("---CHECKLIST_JSON---")) {
            val parts = output.split("---CHECKLIST_JSON---")
            val content = parts[0].trim()
            val jsonPart = parts.getOrNull(1)?.trim()?.replace("```json", "")?.replace("```", "")?.trim() ?: "[]"
            return GenerationResult(
                text = content,
                checklistJson = jsonPart
            )
        }

        // Generate JSON checklist from text if tasks format
        if (type == DeliverableType.MEETING_TASKS) {
            val lines = output.lines()
            val taskList = mutableListOf<JSONObject>()
            for (line in lines) {
                if (line.trim().startsWith("- [ ]") || line.trim().startsWith("• [ ]") || (line.trim().startsWith("- ") && line.contains(":"))) {
                    val cleanText = line.replace("- [ ]", "").replace("• [ ]", "").replace("- ", "").trim()
                    taskList.add(JSONObject().apply {
                        put("text", cleanText)
                        put("isDone", false)
                        put("assignee", if (isEnglish) "Team" else "Équipe")
                        put("deadline", if (isEnglish) "TBD" else "À définir")
                    })
                }
            }
            val jsonStr = JSONArray(taskList).toString()
            return GenerationResult(text = output, checklistJson = jsonStr)
        }

        return GenerationResult(text = output, checklistJson = "[]")
    }

    private fun generateLocalStructuredDeliverable(
        rawTranscript: String,
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String?,
        language: String
    ): GenerationResult {
        val cleanRaw = rawTranscript.trim()
        val isFrench = !language.startsWith("en", ignoreCase = true)
        val titleCandidate = cleanRaw.lines().firstOrNull()?.take(50) ?: if (isFrench) "Note vocale" else "Voice note"

        return when (type) {
            DeliverableType.EMAIL_PRO -> {
                val subject = if (isFrench) "Objet : Point sur $titleCandidate" else "Subject: Update on $titleCandidate"
                val greeting = when (tone) {
                    ToneType.FORMAL -> if (isFrench) "Bonjour,\n\nJ'espère que vous allez bien." else "Dear Colleague,\n\nI hope this email finds you well."
                    ToneType.CASUAL -> if (isFrench) "Salut !\n\nPetit retour rapide suite à notre échange :" else "Hi there!\n\nQuick follow-up following our discussion:"
                    ToneType.SYNTHETIC -> if (isFrench) "Bonjour," else "Hello,"
                    ToneType.DYNAMIC -> if (isFrench) "Bonjour à toute l'équipe,\n\nC'est parti pour le récapitulatif !" else "Hello Team,\n\nExcited to share this update!"
                }
                val body = if (cleanRaw.isNotBlank()) cleanRaw else if (isFrench) "Voici les points abordés lors de notre session de travail." else "Here are the points discussed during our working session."
                val closing = when (tone) {
                    ToneType.FORMAL -> if (isFrench) "\n\nRestant à votre entière disposition pour tout échange complémentaire,\n\nBien cordialement,\n[Votre Nom]" else "\n\nPlease let me know if you need any additional information.\n\nBest regards,\n[Your Name]"
                    ToneType.CASUAL -> if (isFrench) "\n\nDis-moi ce que tu en penses et à très vite !\n\nÀ bientôt,\n[Votre Prénom]" else "\n\nLet me know your thoughts!\n\nCheers,\n[Your Name]"
                    ToneType.SYNTHETIC -> if (isFrench) "\n\nCordialement,\n[Votre Nom]" else "\n\nRegards,\n[Your Name]"
                    ToneType.DYNAMIC -> if (isFrench) "\n\nOn avance ensemble sur ces priorités !\n\nExcellente journée,\n[Votre Nom]" else "\n\nLet's make it happen!\n\nBest,\n[Your Name]"
                }
                val text = "$subject\n\n$greeting\n\n$body$closing"
                GenerationResult(text = text, checklistJson = "[]", rawTranscript = cleanRaw)
            }

            DeliverableType.MEETING_TASKS -> {
                val header = if (isFrench) "📋 COMPTE-RENDU & PLAN D'ACTION" else "📋 MEETING NOTES & ACTION PLAN"
                val summaryTitle = if (isFrench) "1. Résumé exécutif :" else "1. Executive Summary:"
                val decisionsTitle = if (isFrench) "2. Décisions clés :" else "2. Key Decisions:"
                val actionsTitle = if (isFrench) "3. Actions & Checklist :" else "3. Action Items & Checklist:"

                val sentences = cleanRaw.split(Regex("[.!?\n]")).filter { it.isNotBlank() }
                val decisions = if (sentences.isNotEmpty()) {
                    sentences.take(2).joinToString("\n") { "• $it" }
                } else {
                    if (isFrench) "• Validation du plan de lancement\n• Alignement sur les priorités du sprint" else "• Launch plan approved\n• Sprint priorities aligned"
                }

                val tasks = if (sentences.size > 2) {
                    sentences.drop(2).take(4).map { it.trim() }
                } else {
                    if (isFrench) listOf("Finaliser les livrables prioritaires", "Partager le compte-rendu aux parties prenantes", "Planifier le point de suivi")
                    else listOf("Finalize priority deliverables", "Share notes with stakeholders", "Schedule follow-up sync")
                }

                val actionListText = tasks.joinToString("\n") { "- [ ] $it" }
                val fullText = "$header\n\n$summaryTitle\n${cleanRaw.take(300)}\n\n$decisionsTitle\n$decisions\n\n$actionsTitle\n$actionListText"

                val jsonArray = JSONArray()
                tasks.forEach { task ->
                    jsonArray.put(JSONObject().apply {
                        put("text", task)
                        put("isDone", false)
                        put("assignee", if (isFrench) "Responsable" else "Owner")
                        put("deadline", if (isFrench) "Cette semaine" else "This week")
                    })
                }

                GenerationResult(text = fullText, checklistJson = jsonArray.toString(), rawTranscript = cleanRaw)
            }

            DeliverableType.CONTENT_CREATOR -> {
                val linkedInHeader = if (isFrench) "🚀 POST LINKEDIN (Format court & percutant)" else "🚀 LINKEDIN POST (Concise & Impactful)"
                val hook = if (isFrench) "👉 La clé pour transformer une simple idée en résultat concret ?" else "👉 The real key to turning raw ideas into tangible momentum?"
                val takeawayHeader = if (isFrench) "💡 Ce qu'il faut en retenir :" else "💡 Key Takeaways:"
                val takeaway1 = if (isFrench) "• L'action prime sur la réflexion prolongée" else "• Execution beats prolonged contemplation"
                val takeaway2 = if (isFrench) "• Structurer ses pensées permet de gagner des heures chaque semaine" else "• Structuring thoughts saves hours every single week"
                val takeaway3 = if (isFrench) "• Les bons outils amplifient la clarté" else "• The right tools amplify clarity and velocity"
                val cta = if (isFrench) "💬 Et vous, quelle méthode utilisez-vous pour capturer vos éclairs de génie ? Partagez vos astuces en commentaire !" else "💬 How do you capture and structure your best ideas on the go? Drop your thoughts below!"
                val hashtags = if (isFrench) "#Productivite #IntelligenceArtificielle #Organisation #Leadership #Innovation" else "#Productivity #ArtificialIntelligence #Workflow #Leadership #Innovation"

                val linkedInBody = """
$linkedInHeader

$hook

$cleanRaw

$takeawayHeader
$takeaway1
$takeaway2
$takeaway3

$cta

$hashtags
                """.trimIndent()

                val videoScript = if (isFrench) """
🎬 SCRIPT VIDÉO COURT (TikTok / Reels / Shorts)

[0-3s] ACCROCHE :
« Arrêtez de perdre vos meilleures idées sous la douche ou en marchant ! »

[4-30s] DÉVELOPPEMENT :
« Regardez comment un simple mémo vocal de 30 secondes se transforme en email professionnel et en plan d'action immédiat grâce à l'IA. »
(Plan sur l'enregistrement -> Plan sur le texte généré instantanément)

[31-45s] APPEL À L'ACTION :
« Testez dès aujourd'hui et libérez votre esprit. Le lien est disponible sur notre profil ! »
                """.trimIndent() else """
🎬 SHORT VIDEO SCRIPT (TikTok / Reels / Shorts)

[0-3s] HOOK:
"Stop losing your best ideas while walking or commuting!"

[4-30s] CORE INSIGHT:
"Watch how a quick 30-second voice memo transforms into an executive email and an actionable checklist in seconds using AI."
(Cut to recording audio -> cut to instant generated deliverable)

[31-45s] CALL TO ACTION:
"Try it today and keep your workflow moving effortlessly. Link in bio!"
                """.trimIndent()

                GenerationResult(
                    text = "$linkedInBody\n\n--------------------------------\n\n$videoScript",
                    checklistJson = "[]",
                    rawTranscript = cleanRaw
                )
            }

            DeliverableType.CUSTOM -> {
                val header = if (isFrench) "🎯 GABARIT PERSONNALISÉ" else "🎯 CUSTOM TEMPLATE"
                val instructionLabel = if (isFrench) "Consigne appliquée :" else "Applied instruction:"
                val customPrompt = customInstruction ?: if (isFrench) "Synthèse structurée" else "Structured summary"
                val text = """
$header
$instructionLabel $customPrompt

$cleanRaw
                """.trimIndent()
                GenerationResult(text = text, checklistJson = "[]", rawTranscript = cleanRaw)
            }
        }
    }
}

data class GenerationResult(
    val text: String,
    val checklistJson: String,
    val rawTranscript: String = "",
    val isAiPowered: Boolean = true,
    val errorMessage: String? = null
)
