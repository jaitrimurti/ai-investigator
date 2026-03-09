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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wasserbell.aiinvestigator.util.openUrl
import androidx.compose.ui.unit.dp

/**
 * Screen displayed at the end of a session.
 *
 * @param message The text to show to the user.
 * @param followOnUrl If not null, a continue button will be shown that launches this URL.
 * @param onComplete Called when the screen is complete.
 */
@Composable
fun DebriefScreen(message: String, followOnUrl: String?, onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
        )
        if (followOnUrl != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    openUrl(followOnUrl)
                    onComplete()
                },
            ) {
                Text(text = "Continue")
            }
        }
    }
}
