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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** [AiService] for Google Gemini. */
class GeminiAiService(
    private val apiKey: String,
    private val httpClient: HttpClient,
) : AiService {

    override suspend fun sendMessage(conversation: List<ChatMessage>): String {
        if (apiKey.isBlank()) {
            return "Error: Gemini API Key is missing. Please check your config.txt."
        }
        if (AppConfig.modelName.isBlank()) {
            return "Error: Gemini model name is missing. Please check your config.txt."
        }
        if (conversation.isEmpty()) {
            return "Error: Conversation is empty."
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/${AppConfig.modelName}:generateContent?key=$apiKey"

        val contents = mutableListOf<GeminiContent>()

        if (AppConfig.agentInstructions.isNotBlank()) {
            // I should be using the system instruction field to pass the agent instructions, but
            // this hack is an easy solution that does also work. If it causes a problem change this
            // block to pass the instructions in the correct request field.
            contents.add(
                GeminiContent(
                    role = "user",
                    parts =
                        listOf(
                            GeminiPart(
                                text =
                                    buildString {
                                        append("System Objective: ${AppConfig.agentInstructions}")
                                        append("\n\nAcknowledge this objective and behave ")
                                        append("accordingly for the remainder of this session.")
                                    }
                            )
                        )
                )
            )
            contents.add(
                GeminiContent(
                    role = "model",
                    parts =
                        listOf(
                            GeminiPart(
                                text = "I understand and will follow the provided system objective."
                            )
                        )
                )
            )
        }

        conversation.forEach {
            contents.add(
                GeminiContent(
                    role = if (it.isFromUser) "user" else "model",
                    parts = listOf(GeminiPart(text = it.text))
                )
            )
        }

        val requestBody = GeminiRequest(contents = contents)

        try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            // If success is reported, parse and return the response.
            // We check for an empty message as well to have better behavior if nothing is returned.
            if (response.status.isSuccess()) {
                val text =
                    runCatching { response.body<GeminiResponse>() }
                        .getOrNull()
                        ?.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                return if (text.isNullOrBlank()) {
                    "AI returned an empty response."
                } else {
                    text.redacted()
                }
            }

            // If a failure is reported, attempt to parse the error message and return it.
            val error = runCatching { response.body<GeminiErrorResponse>() }.getOrNull()?.error
            if (!error?.message.isNullOrBlank()) {
                return buildString {
                    append("Error")
                    if (!error.status.isNullOrBlank()) {
                        append(" ")
                        append(error.status)
                    } else if (error.code != null) {
                        append(" ")
                        append(error.code)
                    }
                    append(": ")
                    append(error.message.redacted())
                }
            }

            // Fallback to returning the actual response code to help us debug unexpected error
            // scenarios, such as from updates to the Gemini api.
            return "Error: API returned status ${response.status}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Network Request Failed: ${e.message?.redacted()}"
        }
    }
}

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

/** Body of a request to send to the Gemini API. */
@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
)

/** An individual content block in the conversation to send to the Gemini API. */
@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String, // "user" or "model"
)

/** An individual part of a content block. */
@Serializable
private data class GeminiPart(
    val text: String? = null,
)

/** Body of a success response from the Gemini API. */
@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
)

/** An individual candidate response from the Gemini API. */
@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
)

/** The body of a failure response from the Gemini API. */
@Serializable
private data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null,
)

/** Error information from the Gemini API. */
@Serializable
private data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
