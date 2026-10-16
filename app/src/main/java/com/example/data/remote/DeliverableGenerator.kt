package com.example.data.remote

import com.example.BuildConfig
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

    suspend fun generateDeliverable(
        rawTranscript: String,
        type: DeliverableType,
        tone: ToneType,
        customInstruction: String? = null,
        language: String = "fr"
    ): GenerationResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        val isEnglish = language.startsWith("en", ignoreCase = true)
        var lastError: String? = null
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = buildPrompt(rawTranscript, type, tone, customInstruction, language)
                val responseText = callGeminiRest(apiKey, prompt)
                if (responseText.isNotBlank()) {
                    val parsed = parseDeliverableOutput(responseText, type, isEnglish)
                    return@withContext parsed.copy(isAiPowered = true, errorMessage = null)
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: if (isEnglish) "Network / Gemini API error" else "Erreur réseau / API Gemini"
            }
        } else {
            lastError = if (isEnglish) "Gemini API key not configured (local engine active)" else "Clé API Gemini non configurée (moteur local actif)"
        }

        // Fallback engine: transforms voice notes with rich heuristics and structural templates
        val fallback = generateLocalStructuredDeliverable(rawTranscript, type, tone, customInstruction, language)
        return@withContext fallback.copy(isAiPowered = false, errorMessage = lastError)
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

        val formatRequirement = if (isEnglish) {
            when (type) {
                DeliverableType.EMAIL_PRO -> """
                    Transform this voice transcription into a perfect, professional business EMAIL in ENGLISH.
                    Mandatory structure:
                    - Subject: [Clear, compelling and explicit subject line in English]
                    - Professional greeting (e.g., Dear Team, / Hello [Name],)
                    - Email body: clear, well-structured into 2 to 3 concise paragraphs, zero fluff.
                    - Professional sign-off (e.g., Best regards, / Sincerely,)
                    - Signature placeholder: [Name / Title]
                """.trimIndent()

                DeliverableType.MEETING_TASKS -> """
                    Transform this voice transcription into a STRUCTURED MEETING SUMMARY & ACTION PLAN in ENGLISH.
                    Mandatory structure:
                    1. EXECUTIVE SUMMARY (2-3 key sentences in English)
                    2. KEY DECISIONS (bulleted list of agreed decisions in English)
                    3. ACTION ITEMS & CHECKLIST (clear task list formatted as '- [ ] Action: Owner | Deadline' in English)
                    
                    After the main text, add a special section delimited by `---CHECKLIST_JSON---` containing a valid JSON array of objects with keys "text" (task title in English), "assignee" (person or "Team"), and "deadline" (if mentioned or "TBD").
                """.trimIndent()

                DeliverableType.CONTENT_CREATOR -> """
                    Transform this voice transcription into READY-TO-PUBLISH EDITORIAL CONTENT in ENGLISH.
                    Provide two distinct options:
                    
                    === OPTION 1: LINKEDIN POST (Max 1300 characters in English) ===
                    - Compelling hook (1-2 lines creating curiosity)
                    - Clean body (spacious line breaks, professional emojis used moderately, bullet points)
                    - Concrete insight or actionable lesson
                    - Call to action (engaging closing question)
                    - 3 to 5 strategic hashtags in English
                    
                    === OPTION 2: SHORT VIDEO SCRIPT (Reels / TikTok / Shorts in English) ===
                    - 0-3s [Visual & Verbal Hook]
                    - 4-30s [Core Insight / Demo / Storytelling]
                    - 31-45s [Punchy ending & Clear Call to Action]
                """.trimIndent()

                DeliverableType.CUSTOM -> """
                    Apply the following custom instruction to this voice transcription in ENGLISH:
                    Instruction: ${customInstruction ?: "Synthesize and structure this content into a professional executive deliverable in English."}
                """.trimIndent()
            }
        } else {
            when (type) {
                DeliverableType.EMAIL_PRO -> """
                    Transforme cette transcription vocale en un E-MAIL PROFESSIONNEL parfait en français.
                    Structure obligatoire :
                    - Objet : [Un objet clair, percutant et explicite]
                    - Formule de politesse d'ouverture (ex: Bonjour Madame / Monsieur,)
                    - Corps de l'email : clair, bien découpé en 2 à 3 paragraphes concis, sans remplissage inutile.
                    - Formule de politesse de clôture (ex: Bien cordialement,)
                    - Signature type : [Nom / Fonction]
                """.trimIndent()

                DeliverableType.MEETING_TASKS -> """
                    Transforme cette transcription en un COMPTE-RENDU STRUCTURÉ AVEC PLAN D'ACTION en français.
                    Structure obligatoire :
                    1. RÉSUMÉ EXÉCUTIF (2-3 phrases clés)
                    2. DÉCISIONS CLÉS VALIDÉES (liste à puces des décisions prises)
                    3. PLAN D'ACTIONS & TÂCHES (liste de tâches claires sous la forme '- [ ] Action: Responsable | Échéance')
                    
                    Après le texte principal, ajoute une section spéciale délimitée par `---CHECKLIST_JSON---` contenant un tableau JSON d'objets avec les champs "text" (intitulé de la tâche), "assignee" (personne ou "Équipe"), et "deadline" (si mentionnée ou "À définir").
                """.trimIndent()

                DeliverableType.CONTENT_CREATOR -> """
                    Transforme cette transcription en un CONTENU ÉDITORIAL PRÊT À PUBLIER en français.
                    Fournis deux variantes bien distinctes :
                    
                    === OPTION 1 : POST LINKEDIN (Max 1300 caractères) ===
                    - Accroche percutante (1-2 lignes qui créent la curiosité)
                    - Corps aéré (sauts de lignes réguliers, emojis professionnels avec modération, bullet points)
                    - Enseignement ou perspective concrète
                    - Appel à l'action (CTA engageant posant une question)
                    - 3 à 5 Hashtags stratégiques
                    
                    === OPTION 2 : SCRIPT VIDÉO COURT (Reels / TikTok / Shorts) ===
                    - 0-3s [Accroche visuelle & verbale]
                    - 4-30s [Développement / Démonstration / Storytelling]
                    - 31-45s [Chute & Appel à l'action clair]
                """.trimIndent()

                DeliverableType.CUSTOM -> """
                    Applique la consigne personnalisée suivante à cette transcription vocale en français :
                    Consigne : ${customInstruction ?: "Synthétise et structure ce texte de manière professionnelle."}
                """.trimIndent()
            }
        }

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

    private fun callGeminiRest(apiKey: String, prompt: String): String {
        // API key is sent via HTTP header instead of URL query parameter
        // to prevent exposure in network logs, proxies, and request history
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
                GenerationResult(text = text, checklistJson = "[]")
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

                GenerationResult(text = fullText, checklistJson = jsonArray.toString())
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
                    checklistJson = "[]"
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
                GenerationResult(text = text, checklistJson = "[]")
            }
        }
    }
}

data class GenerationResult(
    val text: String,
    val checklistJson: String,
    val isAiPowered: Boolean = true,
    val errorMessage: String? = null
)
