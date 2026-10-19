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
import kotlinx.coroutines.CancellationException
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
        if (conversation.isEmpty()) {
            return "Error: Conversation is empty."
        }

        val url = "https://api.openai.com/v1/chat/completions"
        val messages = mutableListOf<OpenAiRequestMessage>()

        if (AppConfig.agentInstructions.isNotBlank()) {
            messages.add(
                OpenAiRequestMessage(
                    role = when {
                        LEGACY_MODELS.contains(AppConfig.modelName) -> "system"
                        else -> "developer"
                    },
                    content = AppConfig.agentInstructions
                )
            )
        }

        conversation.forEach {
            messages.add(
                OpenAiRequestMessage(
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
                val text = message?.content ?: message?.refusal
                return if (text.isNullOrBlank()) {
                    "AI returned an empty response."
                } else {
                    text.redacted()
                }
            }

            // If a failure is reported, attempt to parse the error message and return it.
            val error = runCatching { response.body<OpenAiErrorResponse>() }.getOrNull()?.error
            if (!error?.message.isNullOrBlank()) {
                return buildString {
                    append("Error")
                    if (!error.code.isNullOrBlank()) {
                        append(" ")
                        append(error.code)
                    }
                    append(": ")
                    append(error.message.redacted())
                }
            }

            // Fallback to returning the actual response code to help us debug unexpected error
            // scenarios, such as from updates to the Open AI api.
            return "Error: API returned status ${response.status}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Network Request Failed: ${e.message?.redacted()}"
        }
    }
}

/** Legacy models use the "system" role for agent instructions. */
private val LEGACY_MODELS = listOf(
    "gpt-4o",
    "gpt-4o-mini",
    "gpt-4-turbo",
    "gpt-4-turbo-preview",
    "gpt-4",
    "gpt-4-32k",
    "gpt-3.5-turbo",
    "gpt-3.5-turbo-0125",
    "gpt-3.5-turbo-1106",
)

private const val REDACTED = "[REDACTED]"

private fun String.redacted(): String =
    redact(AppConfig.agentInstructions)
        .redact(AppConfig.apiKey)
        .redact(AppConfig.remoteLogApiKey)

private fun String.redact(str: String?): String =
    if (str.isNullOrBlank()) {
        this
    } else {
        replace(str, REDACTED)
    }

///////////////////////////////////////////////////////////////////////////////////////////////////
// DTO classes for easier construction and serialization of requests
///////////////////////////////////////////////////////////////////////////////////////////////////

/** Body of a request to send to the Open AI API. */
@Serializable
private data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiRequestMessage>,
)

/** An individual message in the conversation to send to the Open AI API. */
@Serializable
private data class OpenAiRequestMessage(
    val role: String, // "developer", "system" (Only for legacy), "user", "assistant"
    val content: String? = null,
)

/** Body of a success response from the Open AI API. */
@Serializable
private data class OpenAiResponse(
    val choices: List<OpenAiChoice>? = null,
)

/** An individual choice of response from the Open AI API. */
@Serializable
private data class OpenAiChoice(
    val message: OpenAiResponseMessage? = null,
)

/** An individual message in the response from the Open AI API. */
@Serializable
private data class OpenAiResponseMessage(
    val role: String, // "developer", "system" (Only for legacy), "user", "assistant"
    val content: String? = null,
    val refusal: String? = null,
)

/** The body of a failure response from the Open AI API. */
@Serializable
private data class OpenAiErrorResponse(
    val error: OpenAiErrorDetail? = null,
)

/** Error information from the Open AI Api. */
@Serializable
private data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null,
)