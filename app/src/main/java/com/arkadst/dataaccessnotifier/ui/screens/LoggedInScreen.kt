package com.arkadst.dataaccessnotifier.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkadst.dataaccessnotifier.access_logs.LogEntryManager
import com.arkadst.dataaccessnotifier.ui.components.LogEntryItem
import com.arkadst.dataaccessnotifier.userInfoDataStore
import kotlinx.coroutines.flow.map

@Composable
fun LoggedInScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firstNameFlow = remember {
        context.userInfoDataStore.data.map { userInfo ->
            userInfo.firstName
        }
    }
    val firstName by firstNameFlow.collectAsState(initial = "")

    // Use collectAsState to automatically update when new entries are added
    val logEntries by LogEntryManager.loadLogEntriesFlow(context).collectAsState(initial = emptyList())

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        // Welcome message with first name
        if (firstName.isNotEmpty()) {
            Text(
                text = "Welcome, $firstName!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Header row with title and action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Data Access Monitor",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onLogout) {
                    Text(text = "Log out")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Log entries section with improved spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${logEntries.size} entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable list of log entries
        if (logEntries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No activity yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Data access logs will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logEntries.size) { index ->
                    LogEntryItem(logEntry = logEntries[index])
                }
            }
        }
    }
}