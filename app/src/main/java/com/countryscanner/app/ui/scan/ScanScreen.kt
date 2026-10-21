package com.countryscanner.app.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.countryscanner.app.data.model.ScanResult
import java.util.Locale

private val COUNTRIES: List<Pair<String, String>> by lazy {
    Locale.getISOCountries()
        .map { code -> code to Locale("", code).displayCountry }
        .sortedBy { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showCountryDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Country IP & Port Scanner") })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(onClick = { showCountryDialog = true }) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text("Country", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (state.selectedCountry.isBlank()) "Tap to select"
                        else "${state.selectedCountry} - " +
                                (COUNTRIES.firstOrNull { it.first == state.selectedCountry }?.second ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            OutlinedTextField(
                value = state.portsSpec,
                onValueChange = vm::onPortsSpecChanged,
                label = { Text("Ports (80 | 80,443 | 8000-8100 | web | sip)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.startScan() },
                    enabled = !state.isScanning && state.selectedCountry.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isScanning) "Scanning..." else "Start Scan")
                }
                OutlinedButton(
                    onClick = vm::clearResults,
                    enabled = !state.isScanning && state.results.isNotEmpty()
                ) { Text("Clear") }
            }

            if (state.isScanning) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(state.progress * 100).toInt()}% complete",
                    style = MaterialTheme.typography.labelSmall)
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.groupBy == GroupBy.PORT,
                    onClick = { vm.setGroupBy(GroupBy.PORT) },
                    label = { Text("By Port") }
                )
                FilterChip(
                    selected = state.groupBy == GroupBy.ORGANIZATION,
                    onClick = { vm.setGroupBy(GroupBy.ORGANIZATION) },
                    label = { Text("By Organization") }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.results.isEmpty()) {
                    item {
                        Text("No results yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (state.groupBy == GroupBy.PORT) {
                    val grouped = state.results.filter { it.isOpen }.groupBy { it.port }.toSortedMap()
                    grouped.forEach { (port, list) ->
                        item {
                            Text("Port $port", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        items(list) { ResultCard(it) }
                    }
                } else {
                    val grouped = state.results.filter { it.isOpen }
                        .groupBy { it.organization ?: "Unknown Organization" }.toSortedMap()
                    grouped.forEach { (org, list) ->
                        item {
                            Text(org, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        items(list) { ResultCard(it) }
                    }
                }
            }
        }
    }

    if (showCountryDialog) {
        CountryPickerDialog(
            onSelect = { code -> vm.onCountrySelected(code); showCountryDialog = false },
            onDismiss = { showCountryDialog = false }
        )
    }
}

@Composable
private fun CountryPickerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) COUNTRIES
        else COUNTRIES.filter {
            it.first.contains(query, true) || it.second.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { (code, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text(code) },
                            modifier = Modifier.clickable { onSelect(code) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ResultCard(r: ScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(r.ip, style = MaterialTheme.typography.bodyLarge)
            Text("Port ${r.port} - ${r.latencyMs ?: "?"} ms",
                style = MaterialTheme.typography.bodySmall)
            r.organization?.let {
                Text("Org: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
