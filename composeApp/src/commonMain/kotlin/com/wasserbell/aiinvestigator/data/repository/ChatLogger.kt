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

package com.wasserbell.aiinvestigator.data.repository

import com.wasserbell.aiinvestigator.domain.model.ChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Object for logging chat sessions. */
object ChatLogger {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    /**
     * Sends the chat log to the configured database.
     *
     * Returns `true` if successful, otherwise returns `false`.
     * Will do nothing if the database information is not correctly configured.
     */
    suspend fun sendChatLog(participantId: String, messages: List<ChatMessage>): Boolean {
        val remoteLogUrl = AppConfig.remoteLogUrl
        val remoteLogTable = AppConfig.remoteLogTable
        val remoteLogTimestampColumn = AppConfig.remoteLogTimestampColumn
        val remoteLogParticipantIdColumn = AppConfig.remoteLogParticipantIdColumn
        val remoteLogChatLogColumn = AppConfig.remoteLogChatLogColumn
        if (
            remoteLogUrl.isNullOrBlank()
                || remoteLogTable.isNullOrBlank()
                || remoteLogTimestampColumn.isNullOrBlank()
                || remoteLogParticipantIdColumn.isNullOrBlank()
                || remoteLogChatLogColumn.isNullOrBlank()
        ) return false

        val root = JsonObject(
            mapOf(
                "messages" to JsonArray(messages.map { it.toJsonObject() })
            )
        )
        val chatLog = Json.encodeToString(root)

        return try {
            when {
                // When configured with a PostGreSQL address
                remoteLogUrl.startsWith("postgresql://") -> {
                    // Extract host from the connection string
                    val hostMatch = Regex("@([^/?]+)").find(remoteLogUrl)
                    val host = hostMatch?.groupValues?.get(1)
                    
                    if (host != null) {
                        // I'm not sure if all PostGreSQL servers provide an "sql" this way, but
                        // Neon definitely does.
                        val endpoint = "https://$host/sql"
                        val query = buildSqlQuery(
                            table = remoteLogTable,
                            participantIdCol = remoteLogParticipantIdColumn,
                            timestampCol = remoteLogTimestampColumn,
                            chatCol = remoteLogChatLogColumn,
                        )
                        
                        val requestBody = PostGreSqlQueryRequest(
                            query = query,
                            params = listOf(participantId, chatLog)
                        )

                        // CORS policy does not allow setting the content type to application/json,
                        // So we encode the request body as plain text. This is probably a brittle
                        // solution, but it works for now and hopefully at some point I will put
                        // together something more durable.
                        val requestBodyStr = Json.encodeToString(requestBody)
                        httpClient.post(endpoint) {
                            header("neon-connection-string", remoteLogUrl)
                            setBody(requestBodyStr)
                        }
                    }
                }

                // When configured with a standard HTTP/HTTPS address, fallback to a generic REST
                // api. This whole branch is untested. I believe it would work based on the Neon
                // documentation, but I wouldn't be surprised if something is wrong.
                remoteLogUrl.startsWith("http") -> {
                    httpClient.post(remoteLogUrl) {
                        contentType(ContentType.Application.Json)
                        AppConfig.remoteLogApiKey?.let {
                            header("Authorization", "Bearer $it")
                        }
                        setBody(chatLog)
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("Failed to send chat log: ${e.message}")
            false
        }
    }

    private fun buildSqlQuery(
        table: String,
        participantIdCol: String,
        timestampCol: String,
        chatCol: String,
    ): String = buildString {
        append("INSERT INTO ")
        append(table)
        append(" (")
        append(participantIdCol)
        append(", ")
        append(timestampCol)
        append(", ")
        append(chatCol)
        append($$") VALUES ($1, now(), $2::json)")
    }

    @Serializable
    private data class PostGreSqlQueryRequest(
        val query: String,
        val params: List<String>
    )
}
