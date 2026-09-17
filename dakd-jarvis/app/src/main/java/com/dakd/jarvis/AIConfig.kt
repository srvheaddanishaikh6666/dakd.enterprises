package com.dakd.jarvis

import android.content.Context
import com.example.BuildConfig

/**
 * AI Provider types supported by DAKD JARVIS
 */
enum class AIProviderType {
    GEMINI,
    OPENAI,
    CLAUDE,
    CUSTOM,
    OFFLINE
}

/**
 * AI Configuration holder
 */
data class AIConfig(
    val provider: AIProviderType = AIProviderType.GEMINI,
    val endpoint: String = "",
    val model: String = "gemini-3.5-flash",
    val customApiKey: String = "",
    val fallbackProvider: AIProviderType = AIProviderType.OFFLINE
) {
    fun getEffectiveApiKey(): String {
        if (customApiKey.isNotBlank()) return customApiKey
        return when (provider) {
            AIProviderType.GEMINI -> {
                // Read from BuildConfig injected via .env / secrets
                try {
                    BuildConfig.GEMINI_API_KEY
                } catch (e: Throwable) {
                    ""
                }
            }
            else -> ""
        }
    }
}
