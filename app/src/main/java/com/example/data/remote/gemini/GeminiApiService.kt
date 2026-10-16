package com.example.data.remote.gemini

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface communicating directly with the Google Gemini AI API.
 * Supports sending multimodal inputs (including audio bytes encoded in Base64)
 * and receiving structured text or JSON content from the model.
 */
interface GeminiApiService {

    /**
     * Generates structured content from multimodal inputs (audio bytes + text prompts).
     *
     * @param model Model identifier (e.g. "gemini-3.5-flash" or "gemini-2.5-flash")
     * @param apiKey Gemini API key passed via query parameter
     * @param request The request body containing content parts (inline audio data + text prompt)
     * @return Gemini API response containing candidates with structured text/JSON
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-2.5-flash",
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse

    /**
     * Generates content using the API key in the `x-goog-api-key` HTTP header
     * instead of the URL query string to prevent key exposure in logs.
     *
     * @param model Model identifier (e.g. "gemini-2.5-flash")
     * @param apiKey Gemini API key passed via HTTP header
     * @param request The request body containing content parts
     * @return Gemini API response
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentWithHeader(
        @Path("model") model: String = "gemini-2.5-flash",
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}
