package com.example.model

data class CustomTemplate(
    val id: String,
    val name: String,
    val promptInstruction: String,
    val icon: String = "tune"
)
