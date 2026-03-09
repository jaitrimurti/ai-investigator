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

import com.wasserbell.aiinvestigator.data.repository.AppConfig
import com.wasserbell.aiinvestigator.domain.model.ChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** [AiService] for OpenAI ChatGPT. */
class OpenAiService(private val apiKey: String) : AiService {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    override suspend fun sendMessage(history: List<ChatMessage>, newText: String): String {
        if (apiKey.isBlank()) {
            return "Error: OpenAI API Key is missing. Please check your config.txt."
        }
        if (AppConfig.modelName.isBlank()) {
            return "Error: OpenAI model name is missing. Please check your config.txt."
        }

        val url = "https://api.openai.com/v1/chat/completions"
        val messages = mutableListOf<OpenAiMessage>()

        if (AppConfig.agentInstructions.isNotBlank()) {
            messages.add(
                OpenAiMessage(
                    role = "system",
                    content = AppConfig.agentInstructions
                )
            )
        }

        history.forEach {
            messages.add(
                OpenAiMessage(
                    role = if (it.isFromUser) "user" else "assistant",
                    content = it.text
                )
            )
        }

        messages.add(
            OpenAiMessage(
                role = "user",
                content = newText
            )
        )

        val requestBody = OpenAiRequest(model = AppConfig.modelName, messages = messages)

        return try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val openAiResponse = response.body<OpenAiResponse>()
                openAiResponse.choices?.firstOrNull()?.message?.content
                    ?: "AI returned an empty response."
            } else {
                "Error: API returned status ${response.status}. Body: ${response.bodyAsText()}"
            }
        } catch (e: Exception) {
            "Network Request Failed: ${e.message}"
        }
    }
}

///////////////////////////////////////////////////////////////////////////////////////////////////
// DTO classes for easier construction and serialization of requests
///////////////////////////////////////////////////////////////////////////////////////////////////

@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>
)

@Serializable
private data class OpenAiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>? = null
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage? = null
)