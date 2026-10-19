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
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** [AiService] for OpenAI ChatGPT. */
class OpenAiService(
    private val apiKey: String,
    private val httpClient: HttpClient,
) : AiService {

    override suspend fun sendMessage(conversation: List<ChatMessage>): String {
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
                    role = when {
                        // "Reasoning" models use "developer" instead of "system"
                        AppConfig.modelName.startsWith("o1") -> "developer"
                        AppConfig.modelName.startsWith("o3") -> "developer"
                        else -> "system"
                    },
                    content = AppConfig.agentInstructions
                )
            )
        }

        conversation.forEach {
            messages.add(
                OpenAiMessage(
                    role = if (it.isFromUser) "user" else "assistant",
                    content = it.text
                )
            )
        }

        val requestBody = OpenAiRequest(model = AppConfig.modelName, messages = messages)

        try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(requestBody)
            }

            // If success is reported, parse and return the response.
            // The response should be a message or a refusal reason, but we check for an
            // empty message as well to have better behavior if nothing is returned.
            if (response.status.isSuccess()) {
                val message =
                    runCatching { response.body<OpenAiResponse>() }
                        .getOrNull()
                        ?.choices
                        ?.firstOrNull()
                        ?.message
                return message?.content ?: message?.refusal ?: "AI returned an empty response."
            }

            // If a failure is reported, attempt to parse the error message and return it.
            val errorMessage =
                runCatching { response.body<OpenAiErrorResponse>() }.getOrNull()?.error?.message
            if (!errorMessage.isNullOrBlank()) {
                return "Error: $errorMessage"
            }

            // Fallback to returning the actual response code to help us debug unexpected error
            // scenarios, such as from updates to the Open AI api.
            return "Error: API returned status ${response.status}"
        } catch (e: Exception) {
            return "Network Request Failed: ${e.message}"
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
    val content: String? = null,
    val refusal: String? = null
)

@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>? = null
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage? = null
)

@Serializable
private data class OpenAiErrorResponse(
    val error: OpenAiErrorDetail? = null
)

@Serializable
private data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)