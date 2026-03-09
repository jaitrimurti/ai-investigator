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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.wasserbell.aiinvestigator.data.repository.AppConfig
import com.wasserbell.aiinvestigator.data.repository.ChatLogger
import com.wasserbell.aiinvestigator.ui.composable.ChatScreen
import com.wasserbell.aiinvestigator.ui.composable.DebriefScreen

/**
 * Entry point for the app.
 *
 * @param participantId The unique identifier for this user.
 * @param condition The unique identifier for this experimental condition.
 * @param prompt The initial message to be displayed to the user from the AI agent.
 * @param followOnUrl The URL to launch after this app completes.
 * @param sessionDurationOverride Overrides the duration of the chat.
 * @param modelNameOverride Overrides which AI model to use.
 * @param onComplete Called when the app completes.
 */
@Composable
fun App(
    participantId: String? = null,
    condition: String? = null,
    prompt: String? = null,
    followOnUrl: String? = null,
    sessionDurationOverride: String? = null,
    modelNameOverride: String? = null,
    onComplete: () -> Unit = {},
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var appState by remember { mutableStateOf(AppState.LOADING) }

        LaunchedEffect(Unit) {
            AppConfig.loadConfig(
                participantId = participantId ?: "NOT_SET",
                condition = condition ?: "NOT_SET",
                prompt = prompt,
                followOnUrlOverride = followOnUrl,
                sessionDurationOverride = sessionDurationOverride,
                modelNameOverride = modelNameOverride,
            )
            
            appState = AppState.CHAT
        }

        when (appState) {
            AppState.LOADING -> LoadingScreen()
            AppState.DEBRIEF ->
                DebriefScreen(
                    message = AppConfig.sessionEndMessage,
                    followOnUrl = AppConfig.followOnUrl,
                    onComplete = onComplete,
                )

            else -> ChatScreen(
                sessionDuration = AppConfig.sessionDuration,
                returnKeySendsMessage = AppConfig.returnKeySendsMessage,
                initialQuestion = AppConfig.initialQuestion
            ) { messages ->
                appState = AppState.LOADING
                scope.launch {
                    val success = ChatLogger.sendChatLog(
                        AppConfig.participantId,
                        messages
                    )
                    if (success) {
                        println("Successfully sent results")
                    } else {
                        println("Failed to send results")
                    }
                    appState = AppState.DEBRIEF
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private enum class AppState {
    LOADING,
    CHAT,
    DEBRIEF
}
