package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class ToneType(
    @StringRes val labelRes: Int,
    val promptModifier: String
) {
    FORMAL(
        labelRes = R.string.tone_formal,
        promptModifier = "Utilise un ton formel, professionnel, élégant et rigoureux."
    ),
    CASUAL(
        labelRes = R.string.tone_casual,
        promptModifier = "Utilise un ton décontracté, fluide, accessible et percutant."
    ),
    SYNTHETIC(
        labelRes = R.string.tone_synthetic,
        promptModifier = "Utilise un format ultra-concis, direct, en bullet points sans fioritures."
    ),
    DYNAMIC(
        labelRes = R.string.tone_dynamic,
        promptModifier = "Utilise un ton dynamique, engageant, stimulant et tourné vers l'action."
    );

    val label: String
        get() = when (this) {
            FORMAL -> "Formel"
            CASUAL -> "Décontracté"
            SYNTHETIC -> "Synthétique"
            DYNAMIC -> "Dynamique"
        }

    fun getPromptModifier(isEnglish: Boolean): String {
        return if (isEnglish) {
            when (this) {
                FORMAL -> "Use a formal, professional, polished, and rigorous tone."
                CASUAL -> "Use a casual, fluid, accessible, and punchy tone."
                SYNTHETIC -> "Use an ultra-concise, direct format with bullet points and zero fluff."
                DYNAMIC -> "Use a dynamic, engaging, inspiring, and action-oriented tone."
            }
        } else {
            promptModifier
        }
    }
}
