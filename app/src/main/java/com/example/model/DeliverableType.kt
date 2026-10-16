package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class DeliverableType(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val iconName: String
) {
    EMAIL_PRO(
        titleRes = R.string.deliverable_email_title,
        subtitleRes = R.string.deliverable_email_subtitle,
        iconName = "email"
    ),
    MEETING_TASKS(
        titleRes = R.string.deliverable_meeting_title,
        subtitleRes = R.string.deliverable_meeting_subtitle,
        iconName = "task"
    ),
    CONTENT_CREATOR(
        titleRes = R.string.deliverable_content_title,
        subtitleRes = R.string.deliverable_content_subtitle,
        iconName = "campaign"
    ),
    CUSTOM(
        titleRes = R.string.deliverable_custom_title,
        subtitleRes = R.string.deliverable_custom_subtitle,
        iconName = "tune"
    );

    val title: String
        get() = when (this) {
            EMAIL_PRO -> "E-mail Pro"
            MEETING_TASKS -> "Compte-rendu & Tâches"
            CONTENT_CREATOR -> "Contenu Créateur"
            CUSTOM -> "Gabarit Personnalisé"
        }

    val subtitle: String
        get() = when (this) {
            EMAIL_PRO -> "Clair, structuré et prêt à l'envoi"
            MEETING_TASKS -> "Points clés, décisions & checklist"
            CONTENT_CREATOR -> "Post LinkedIn ou script vidéo court"
            CUSTOM -> "Reformulation sur mesure selon vos consignes"
        }
}
