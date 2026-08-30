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

        var lastError: String? = null
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val prompt = buildPrompt(rawTranscript, type, tone, customInstruction, language)
                val responseText = callGeminiRest(apiKey, prompt)
                if (responseText.isNotBlank()) {
                    val parsed = parseDeliverableOutput(responseText, type)
                    return@withContext parsed.copy(isAiPowered = true, errorMessage = null)
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Erreur réseau / API Gemini"
            }
        } else {
            lastError = "Clé API Gemini non configurée (moteur local actif)"
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
        val langStr = if (language.startsWith("en", ignoreCase = true)) "en anglais" else "en français"
        val toneInstruction = tone.promptModifier

        val formatRequirement = when (type) {
            DeliverableType.EMAIL_PRO -> """
                Transforme cette transcription vocale en un E-MAIL PROFESSIONNEL parfait $langStr.
                Structure obligatoire :
                - Objet : [Un objet clair, percutant et explicite]
                - Formule de politesse d'ouverture (ex: Bonjour Madame / Monsieur,)
                - Corps de l'email : clair, bien découpé en 2 à 3 paragraphes concis, sans remplissage inutile.
                - Formule de politesse de clôture (ex: Bien cordialement,)
                - Signature type : [Nom / Fonction]
            """.trimIndent()

            DeliverableType.MEETING_TASKS -> """
                Transforme cette transcription en un COMPTE-RENDU STRUCTURÉ AVEC PLAN D'ACTION $langStr.
                Structure obligatoire :
                1. RÉSUMÉ EXÉCUTIF (2-3 phrases clés)
                2. DÉCISIONS CLÉS VALIDÉES (liste à puces des décisions prises)
                3. PLAN D'ACTIONS & TÂCHES (liste de tâches claires sous la forme '- [ ] Action: Responsable | Échéance')
                
                Après le texte principal, ajoute une section spéciale délimitée par `---CHECKLIST_JSON---` contenant un tableau JSON d'objets avec les champs "text" (intitulé de la tâche), "assignee" (personne ou "Équipe"), et "deadline" (si mentionnée ou "À définir").
            """.trimIndent()

            DeliverableType.CONTENT_CREATOR -> """
                Transforme cette transcription en un CONTENU ÉDITORIAL PRÊT À PUBLIER $langStr.
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
                Applique la consigne personnalisée suivante à cette transcription vocale $langStr :
                Consigne : ${customInstruction ?: "Synthétise et structure ce texte de manière professionnelle."}
            """.trimIndent()
        }

        return """
            Tu es l'assistant de productivité expert de l'application 'Dictaphone IA - De la voix au flux de travail'.
            Ta mission est de convertir une capture vocale spontanée, potentiellement hésitante ou désordonnée, en un livrable professionnel soigné et directement exploitable.
            
            Consignes éditoriales :
            - Langue cible : $langStr
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

    private fun parseDeliverableOutput(output: String, type: DeliverableType): GenerationResult {
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
                        put("assignee", "Équipe")
                        put("deadline", "À définir")
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
                val linkedInHeader = "🚀 POST LINKEDIN (Format court & percutant)"
                val hook = if (isFrench) "👉 La clé pour transformer une simple idée en résultat concret ?" else "👉 The real key to turning raw ideas into tangible momentum?"
                val linkedInBody = """
$linkedInHeader

$hook

$cleanRaw

💡 Ce qu'il faut en retenir :
• L'action prime sur la réflexion prolongée
• Structurer ses pensées permet de gagner des heures chaque semaine
• Les bons outils amplifient la clarté

💬 Et vous, quelle méthode utilisez-vous pour capturer vos éclairs de génie ? Partagez vos astuces en commentaire !

#Productivite #IntelligenceArtificielle #Organisation #Leadership #Innovation
                """.trimIndent()

                val videoScript = """
🎬 SCRIPT VIDÉO COURT (TikTok / Reels / Shorts)

[0-3s] ACCROCHE :
« Arrêtez de perdre vos meilleures idées sous la douche ou en marchant ! »

[4-30s] DÉVELOPPEMENT :
« Regardez comment un simple mémo vocal de 30 secondes se transforme en email professionnel et en plan d'action immédiat grâce à l'IA. »
(Plan sur l'enregistrement -> Plan sur le texte généré instantanément)

[31-45s] APPEL À L'ACTION :
« Testez dès aujourd'hui et libérez votre esprit. Le lien est disponible sur notre profil ! »
                """.trimIndent()

                GenerationResult(
                    text = "$linkedInBody\n\n--------------------------------\n\n$videoScript",
                    checklistJson = "[]"
                )
            }

            DeliverableType.CUSTOM -> {
                val customPrompt = customInstruction ?: if (isFrench) "Synthèse structurée" else "Structured summary"
                val text = """
🎯 GABARIT PERSONNALISÉ
Consigne appliquée : $customPrompt

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
