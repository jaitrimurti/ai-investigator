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

package com.wasserbell.aiinvestigator.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A message in a conversation with the AI agent. */
data class ChatMessage(
    /** Unique identifier for this message. */
    val id: String,
    /** The text of the message. */
    val text: String,
    /** `true` if this was sent by the participant, `false` if this was sent by the AI agent. */
    val isFromUser: Boolean,
    /** When the message was sent. */
    val timestamp: Long = getCurrentTimeMillis()
) {
    /** Returns a [JsonObject] of this [ChatMessage]. */
    fun toJsonObject(): JsonObject = 
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "timestamp" to JsonPrimitive(timestamp),
                "text" to JsonPrimitive(text),
                "role" to JsonPrimitive(if (isFromUser) "participant" else "ai")
            )
        )
}

