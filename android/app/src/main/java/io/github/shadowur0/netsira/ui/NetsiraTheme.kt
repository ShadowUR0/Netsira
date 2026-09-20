// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2563EB)
private val LightColors = lightColorScheme(primary = Brand)
private val DarkColors = darkColorScheme(primary = Color(0xFF93C5FD))

@Composable
fun NetsiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
