// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.shadowur0.netsira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shadowur0.netsira.ui.NetsiraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetsiraTheme {
                NetsiraApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetsiraApp() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Netsira")
                        Text("Network diagnostics", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Diagnostics", style = MaterialTheme.typography.headlineMedium) }
            item {
                DiagnosticCard(
                    title = "Quick test",
                    description = "A short health snapshot using the checks available on this phone."
                )
            }
            item {
                DiagnosticCard(
                    title = "Comprehensive test",
                    description = "All supported stages with correlated findings and evidence."
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = { Text("Phase 2", style = MaterialTheme.typography.labelMedium) }
        )
    }
}
