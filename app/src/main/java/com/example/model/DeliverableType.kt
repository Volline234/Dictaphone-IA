package com.example.model

enum class DeliverableType(
    val title: String,
    val subtitle: String,
    val iconName: String
) {
    EMAIL_PRO(
        title = "E-mail pro",
        subtitle = "Objet, politesse, corps & signature",
        iconName = "email"
    ),
    MEETING_TASKS(
        title = "Compte-rendu & Tâches",
        subtitle = "Décisions & checklist d'actions",
        iconName = "task"
    ),
    CONTENT_CREATOR(
        title = "Créateur de contenu",
        subtitle = "Post LinkedIn / Script vidéo court",
        iconName = "campaign"
    ),
    CUSTOM(
        title = "Gabarit personnalisé",
        subtitle = "Prompt libre réutilisable",
        iconName = "tune"
    )
}
