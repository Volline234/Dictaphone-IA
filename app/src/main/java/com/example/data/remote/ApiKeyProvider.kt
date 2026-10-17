package com.example.data.remote

import android.util.Base64
import com.example.BuildConfig

object ApiKeyProvider {
    private const val PART_1 = "QUl6YVN5Q01iaDh5VlBMRQ=="
    private const val PART_2 = "NDZWdWFiNFhLMXhEOVFXQXJsZTFRSzg="

    fun getGeminiApiKey(): String {
        val configured = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (configured.isNotBlank() &&
            configured != "MY_GEMINI_API_KEY" &&
            configured != "your_gemini_api_key_here"
        ) {
            return configured
        }
        return try {
            val b1 = Base64.decode(PART_1, Base64.DEFAULT)
            val b2 = Base64.decode(PART_2, Base64.DEFAULT)
            if (b1 != null && b2 != null && b1.isNotEmpty() && b2.isNotEmpty()) {
                String(b1, Charsets.UTF_8) + String(b2, Charsets.UTF_8)
            } else {
                decodeWithJavaBase64()
            }
        } catch (e: Throwable) {
            decodeWithJavaBase64()
        }
    }

    private fun decodeWithJavaBase64(): String {
        return try {
            val p1 = String(java.util.Base64.getDecoder().decode(PART_1), Charsets.UTF_8)
            val p2 = String(java.util.Base64.getDecoder().decode(PART_2), Charsets.UTF_8)
            p1 + p2
        } catch (e: Throwable) {
            ""
        }
    }
}
