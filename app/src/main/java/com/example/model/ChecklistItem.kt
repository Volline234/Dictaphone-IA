package com.example.model

data class ChecklistItem(
    val id: String,
    val text: String,
    val isDone: Boolean = false,
    val assignee: String? = null,
    val deadline: String? = null
)
