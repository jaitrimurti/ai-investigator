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

import ai_investigator.composeapp.generated.resources.Res
import androidx.annotation.GuardedBy
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The supported AI Providers.
 *
 * Indicates which API to utilize.
 */
enum class AiProvider {
    /** Initial value, exception will be thrown if not set. */
    NOT_SET,
    /** Google Gemini */
    GEMINI,
    /** Open AI ChatGPT */
    OPENAI,
}

/**
 * Central configuration object
 *
 * Values are populated at runtime from a combination `config.txt` and runtime parameters.
 */
@OptIn(ExperimentalResourceApi::class)
object AppConfig {

    /** Whether [AppConfig] is ready to use. */
    @GuardedBy("mutex")
    var isInitialized: Boolean = false
        private set

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // AI model configuration
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Which AI provider has been selected.
     *
     * Provided by config.txt.
     */
    var aiProvider: AiProvider = AiProvider.NOT_SET
        private set

    /**
     * Which specific AI model has been selected.
     *
     * Provided by config.txt, overridable by runtime param.
     */
    var modelName: String = ""
        private set

    /**
     * The API key to use for AI requests.
     *
     * Provided by config.txt.
     */
    var apiKey: String = ""
        private set

    /**
     * The instructions for the AI agent.
     *
     * Set of available instructions provided by config.txt, selection from set provided by runtime
     * param.
     */
    var agentInstructions: String = ""
        private set

    /**
     * A preseeded prompt
     *
     * If not null, this will appear to the user and the AI agent as opening message from the AI
     * agent.
     *
     * Provided by runtime param.
     */
    var initialQuestion: String? = null
        private set

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Session configuration
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Unique identifier for this session.
     *
     * Provided by runtime param.
     */
    var participantId: String = ""
        private set

    /**
     * How long for the session to last.
     *
     * The session will end after the next message sent by the user after [sessionDuration] has
     * elapsed.
     *
     * Provided by config.txt, overridable by runtime param.
     */
    var sessionDuration: Duration = Duration.INFINITE
        private set

    /**
     * Whether to have the return key send the typed message to the AI agent.
     *
     * Provided by config.txt
     */
    var returnKeySendsMessage: Boolean = true
        private set

    /**
     * The message to display to the user on the debrief screen at the end of the session.
     *
     * Provided by config.txt
     */
    var sessionEndMessage: String = ""
        private set

    /**
     * Url to open in browser once at the end of the session.
     *
     * If not null, a "Continue" button will be presented to the user on the debrief screen and
     * clicking it will open [followOnUrl] in the current web browser. If there is no current web
     * browser, it will open in the default web browser.
     *
     * Provided by config.txt, overridable by runtime param.
     */
    var followOnUrl: String? = null
        private set

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Remote logging configuration
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The URL for a SQL database for recording results.
     *
     * Note: Tested with PostGreSQL running on Neon. Might need some work to support other setups.
     *
     * Provided by config.txt
     */
    var remoteLogUrl: String? = null
        private set

    /**
     * The name of the SQL table for recording results.
     *
     * Note: Tested with PostGreSQL running on Neon. Might need some work to support other setups.
     *
     * Provided by config.txt
     */
    var remoteLogTable: String? = null
        private set

    /**
     * The name of the column of the SQL table for recording timestamps.
     *
     * Note: Tested with PostGreSQL running on Neon. Might need some work to support other setups.
     *
     * Provided by config.txt
     */
    var remoteLogTimestampColumn: String? = null
        private set

    /**
     * The name of the column of the SQL table for recording [participantId].
     *
     * Note: Tested with PostGreSQL running on Neon. Might need some work to support other setups.
     *
     * Provided by config.txt
     */
    var remoteLogParticipantIdColumn: String? = null
        private set

    /**
     * The name of the column of the SQL table for recording chat logs JSON.
     *
     * Note: Tested with PostGreSQL running on Neon. Might need some work to support other setups.
     *
     * Provided by config.txt
     */
    var remoteLogChatLogColumn: String? = null
        private set

    /**
     * API key for accessing the database for logging.
     *
     * Note: Not tested, but provided for future compatibility.
     *
     * Provided by config.txt
     */
    var remoteLogApiKey: String? = null
        private set

    private val mutex = Mutex()
        
    /**
     * Initializes the config from the `config.txt` resource.
     *
     * This function should be called before any of [AppConfig]'s fields are accessed and ignores
     * repeat calls. Parameters should come from application runtime parameters.
     *
     * @param participantId See [AppConfig.participantId].
     * @param condition Selection key for which configured agent instructions to use.
     * @param prompt See [AppConfig.initialQuestion].
     * @param followOnUrlOverride Override value for [AppConfig.followOnUrl].
     * @param sessionDurationOverride Override value for [AppConfig.sessionDuration].
     * @param modelNameOverride Override value for [AppConfig.modelName].
     */
    suspend fun loadConfig(
        participantId: String,
        condition: String,
        prompt: String? = null,
        followOnUrlOverride: String? = null,
        sessionDurationOverride: String? = null,
        modelNameOverride: String? = null,
    ) = mutex.withLock {
        
        if (isInitialized) return

        this.participantId = participantId
        this.initialQuestion = prompt
        
        try {
            val configLines = withContext(Dispatchers.Default) {
                // Try reading from the execution directory first
                val localBytes = readLocalConfigFile()
                val bytes = localBytes ?: Res.readBytes("files/config.txt")
                bytes.decodeToString().lines()
            }
            
            val agentInstructionConditions = mutableMapOf<String, String>()
            var currentKey = ""
            configLines.forEach { line ->
                val trimmed = line.trim()
                
                // Ignore comments
                if (trimmed.startsWith("#")) return@forEach
                
                val isKeyValue =
                    line.contains("=")
                            && !line.substringBefore("=").contains(" ")
                            && line.substringBefore("=").isNotBlank()

                when {
                    // New key found
                    isKeyValue -> {
                        val parts = line.split("=", limit = 2)
                        val key = parts[0].trim().lowercase()
                        val value = parts[1].trim()
                        
                        currentKey = key
                        
                        when {

                            key == "provider" -> {
                                aiProvider = try {
                                    AiProvider.valueOf(value.uppercase())
                                } catch (_: Exception) {
                                    AiProvider.NOT_SET
                                }
                            }

                            key == "model_name" -> modelName = value

                            key == "api_key" -> apiKey = value

                            key == "session_duration" ->
                                this.sessionDuration = Duration.parse(value)

                            key == "return_key_sends_message" ->
                                returnKeySendsMessage = value.lowercase() == "true"

                            key == "session_end_message" -> sessionEndMessage = value

                            key == "remote_log_url" -> remoteLogUrl = value

                            key == "remote_log_table" -> remoteLogTable = value

                            key == "remote_log_timestamp_column" ->
                                remoteLogTimestampColumn = value

                            key == "remote_log_participant_id_column" ->
                                remoteLogParticipantIdColumn = value

                            key == "remote_log_chat_log_column" -> remoteLogChatLogColumn = value

                            key == "remote_log_api_key" -> remoteLogApiKey = value

                            key == "follow_on_url" -> this.followOnUrl = value

                            key.startsWith("agent_instructions_") -> {
                                val conditionKey = key.removePrefix("agent_instructions_")
                                if (conditionKey.isEmpty()) {
                                    throw IllegalArgumentException("Invalid agent instructions key: $key")
                                }
                                agentInstructionConditions[conditionKey] = value
                            }
                        }
                    }

                    // Continuation of session_end_message key
                    currentKey == "session_end_message" -> {
                        sessionEndMessage += "\n" + line
                    }

                    // Continuation for agent_instructions_[condition] key
                    currentKey.startsWith("agent_instructions_") -> {
                        val conditionKey = currentKey.removePrefix("agent_instructions_")
                        if (conditionKey.isEmpty()) {
                            throw IllegalArgumentException("Invalid agent instructions key: $currentKey")
                        }
                        agentInstructionConditions[conditionKey] = agentInstructionConditions[conditionKey] + "\n" + line
                    }
                }
            }

            agentInstructions = agentInstructionConditions[condition]
                ?: agentInstructionConditions.values.firstOrNull()
                ?: throw IllegalArgumentException("No agent instructions found for condition: $condition")

            println("config.txt loaded")
        } catch (e: Exception) {
            // Allow coroutine cancellation
            if (e is CancellationException) throw e

            println("Failed to load config.txt: ${e.message}")
        }

        // Url parameters override config.txt
        sessionDurationOverride?.let { this.sessionDuration = Duration.parse(it) }
        modelNameOverride?.let { this.modelName = it }
        followOnUrlOverride?.let {
            if (it.isEmpty()) {
                this.followOnUrl = null
            } else {
                this.followOnUrl = it
            }
        }

        isInitialized = true

        println("Model: $aiProvider")
        println("Model name: $modelName")
        println("API key: ${if (apiKey.isEmpty()) "NOT SET" else "SET"}")
        println("Agent instructions: ${if (agentInstructions.isEmpty()) "NOT SET" else "SET"}")
        println("Initial question: $initialQuestion")
        println("Participant ID: $participantId")
        println("Session duration: $sessionDuration")
        println("Return key sends message: $returnKeySendsMessage")
        println("Session end message: $sessionEndMessage")
        println("Follow on URL: $followOnUrlOverride")
        println("Remote log URL: $remoteLogUrl")
        println("Remote log API key: ${if (remoteLogApiKey.isNullOrEmpty()) "NOT SET" else "SET"}")
    }
}
