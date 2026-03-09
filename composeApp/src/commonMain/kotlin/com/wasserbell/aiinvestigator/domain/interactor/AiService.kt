/*
 * The AI Investigator application.
 *
 * Copyright (C) 2026 Nathaniel Wasserman <thangov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wasserbell.aiinvestigator.domain.interactor

import com.wasserbell.aiinvestigator.data.repository.AiProvider
import com.wasserbell.aiinvestigator.data.repository.AppConfig
import com.wasserbell.aiinvestigator.domain.model.ChatMessage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An AI API provider. */
interface AiService {
    /**
     * Sends a message and the conversation history to the AI Agent and returns the generated text
     * response.
     */
    suspend fun sendMessage(history: List<ChatMessage>, newText: String): String
}

/**
 * Provides [AiService] objects.
 */
object AiServiceProvider {

    /** Returns the configured [AiService]. */
    fun getService(): AiService {
        return when (AppConfig.aiProvider) {
            AiProvider.GEMINI -> GeminiAiService(AppConfig.apiKey)
            AiProvider.OPENAI -> OpenAiService(AppConfig.apiKey)
            AiProvider.NOT_SET ->
                throw IllegalStateException("AI provider not set. Please check config.txt.")
        }
    }
}
