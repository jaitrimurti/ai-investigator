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

/** [AiService] for Google Gemini. */
class GeminiAiService(private val apiKey: String) : AiService {

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
            return "Error: Gemini API Key is missing. Please check your config.txt."
        }

        val url =
            buildString {
                append("\"https://generativelanguage.googleapis.com/v1beta/models/")
                append(AppConfig.modelName)
                append(":generateContent?")
                append("key=$apiKey")
            }

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

        history.forEach {
            contents.add(
                GeminiContent(
                    role = if (it.isFromUser) "user" else "model",
                    parts = listOf(GeminiPart(text = it.text))
                )
            )
        }

        contents.add(
            GeminiContent(
                role = "user",
                parts = listOf(GeminiPart(text = newText))
            )
        )

        val requestBody = GeminiRequest(contents = contents)

        return try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val geminiResponse = response.body<GeminiResponse>()
                geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
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
private data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String // "user" or "model"
)

@Serializable
private data class GeminiPart(
    val text: String
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null
)
