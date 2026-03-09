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

package com.wasserbell.aiinvestigator.ui.composable

import com.wasserbell.aiinvestigator.domain.interactor.AiServiceProvider
import com.wasserbell.aiinvestigator.domain.model.ChatMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * A text app style chat screen.
 *
 * @param sessionDuration How long before ending the chat session. [onComplete] will be called when
 *                        the first message from the user is received after [sessionDuration] has
 *                        elapsed.
 * @param initialQuestion The initial message from the AI agent.
 * @param returnKeySendsMessage If `true`, pressing the return key will send the current message,
 *                              otherwise it will add a new line to the current message.
 * @param onComplete Called when the chat is complete.
 */
@Composable
fun ChatScreen(
    sessionDuration: Duration,
    initialQuestion: String?,
    returnKeySendsMessage: Boolean = true,
    onComplete: (List<ChatMessage>) -> Unit,
) {
    var messages by remember {
        mutableStateOf(
            if (initialQuestion != null) {
                listOf(
                    ChatMessage(
                        id = "0", 
                        text = initialQuestion, 
                        isFromUser = false
                    )
                )
            } else {
                emptyList()
            }
        )
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isTimeUp by remember { mutableStateOf(false) }

    if (sessionDuration < Duration.INFINITE) {
        LaunchedEffect(Unit) {
            delay(sessionDuration)
            isTimeUp = true
        }
    }

    // Scroll to bottom when a new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))
    ) {
        // Chat History
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            elevation = 8.dp,
            color = Color.White
        ) {
            val scope = rememberCoroutineScope()
            val sendAction = {
                if (inputText.isNotBlank()) {
                    val userText = inputText
                    val newUserMessage = ChatMessage(
                        id = messages.size.toString(),
                        text = userText,
                        isFromUser = true
                    )
                    messages = messages + newUserMessage
                    inputText = ""

                    if (isTimeUp) {
                        onComplete(messages)
                    } else {
                        val loadingId = (messages.size + 1).toString()
                        val loadingMessage = ChatMessage(
                            id = loadingId,
                            text = "Thinking...",
                            isFromUser = false
                        )
                        messages = messages + loadingMessage

                        scope.launch {
                            val aiService = AiServiceProvider.getService()
                            val responseText =
                                aiService.sendMessage(messages.dropLast(1), userText)

                            messages = messages.filter { it.id != loadingId } + ChatMessage(
                                id = loadingId,
                                text = responseText,
                                isFromUser = false
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (
                            returnKeySendsMessage 
                            && event.key == Key.Enter 
                            && event.type == KeyEventType.KeyDown 
                            && !event.isShiftPressed
                        ) {
                            sendAction()
                            true
                        } else {
                            false
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendAction() }),
                    placeholder = { Text("Enter your response here...") },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 3
                )
                
                IconButton(
                    onClick = sendAction,
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint =
                            if (inputText.isNotBlank()) {
                                MaterialTheme.colors.primary
                            } else {
                                Color.Gray
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isFromUser) MaterialTheme.colors.primary else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 0.dp,
                bottomEnd = if (message.isFromUser) 0.dp else 16.dp
            ),
            elevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (message.isFromUser) Color.White else Color.Black
            )
        }
    }
}
