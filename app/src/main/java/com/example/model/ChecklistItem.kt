package com.example.model

data class ChecklistItem(
    val id: String,
    val text: String,
    var isDone: Boolean = false,
    val assignee: String? = null,
    val deadline: String? = null
)
