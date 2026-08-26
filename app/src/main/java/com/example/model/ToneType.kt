package com.example.model

enum class ToneType(
    val label: String,
    val promptModifier: String
) {
    FORMAL(
        label = "Formel",
        promptModifier = "Utilise un ton formel, professionnel, élégant et rigoureux."
    ),
    CASUAL(
        label = "Décontracté",
        promptModifier = "Utilise un ton décontracté, fluide, accessible et percutant."
    ),
    SYNTHETIC(
        label = "Synthétique",
        promptModifier = "Utilise un format ultra-concis, direct, en bullet points sans fioritures."
    ),
    DYNAMIC(
        label = "Dynamique / Inspirant",
        promptModifier = "Utilise un ton dynamique, engageant, stimulant et tourné vers l'action."
    )
}
