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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import kotlinx.browser.window
import io.ktor.http.decodeURLQueryComponent

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    var participantId: String? = null
    var condition: String? = null
    var prompt: String? = null
    var followOnUrl: String? = null
    var sessionDurationOverride: String? = null
    var modelNameOverride: String? = null
    
    try {
        val search = window.location.search
        if (search.startsWith("?")) {
            val params = search.drop(1).split("&")
            for (param in params) {
                val kv = param.split("=")
                val key = kv[0]
                val value = kv.getOrNull(1)
                when (key) {
                    "pid" -> participantId = value?.decodeURLQueryComponent(plusIsSpace = true)
                    "cond" -> condition = value?.decodeURLQueryComponent(plusIsSpace = true)
                    "prompt" -> prompt = value?.decodeURLQueryComponent(plusIsSpace = true)
                    "next" -> followOnUrl = value?.decodeURLQueryComponent(plusIsSpace = true)
                    "no_next" -> followOnUrl = ""
                    "time" -> sessionDurationOverride = value?.decodeURLQueryComponent(plusIsSpace = true)
                    "model" -> modelNameOverride = value?.decodeURLQueryComponent(plusIsSpace = true)
                }
            }
        }
    } catch (e: Exception) {
        println("Error parsing URL params: ${e.message}")
    }

    CanvasBasedWindow(canvasElementId = "ComposeTarget") { 
        App(
            participantId = participantId,
            condition = condition,
            prompt = prompt,
            followOnUrl = followOnUrl,
            sessionDurationOverride = sessionDurationOverride,
            modelNameOverride = modelNameOverride,
        ) {
            window.close()
        }
    }
}
