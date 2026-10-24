package com.countryscanner.app.ui.scan

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countryscanner.app.data.model.ScanResult
import com.countryscanner.app.data.repository.CountryIpRepository
import com.countryscanner.app.data.repository.ScanRepository
import com.countryscanner.app.domain.PortPresets
import com.countryscanner.app.domain.ScanEngine
import com.countryscanner.app.util.sampleTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class GroupBy { PORT, ORGANIZATION }

data class ScanUiState(
    val selectedCountry: String = "",
    val portsSpec: String = "common",
    val maxBlocks: Int = 20,
    val maxHosts: Int = 50,
    val timeoutMs: Int = 2000,
    val isLoadingCidrs: Boolean = false,
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val logs: List<String> = emptyList(),
    val results: List<ScanResult> = emptyList(),
    val groupBy: GroupBy = GroupBy.PORT,
    val error: String? = null,
    val exportMessage: String? = null,
    val showIpsOnly: Boolean = false
)

class ScanViewModel(
    private val countryRepo: CountryIpRepository = CountryIpRepository(),
    private val scanRepo: ScanRepository = ScanRepository(),
    private val scanEngine: ScanEngine = ScanEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun onCountrySelected(code: String) {
        _state.update { it.copy(selectedCountry = code, error = null) }
    }

    fun onPortsSpecChanged(spec: String) {
        _state.update { it.copy(portsSpec = spec) }
    }

    fun onBlocksChanged(v: Int) {
        _state.update { it.copy(maxBlocks = v.coerceIn(1, 200)) }
    }

    fun onHostsChanged(v: Int) {
        _state.update { it.copy(maxHosts = v.coerceIn(5, 256)) }
    }

    fun onTimeoutChanged(v: Int) {
        _state.update { it.copy(timeoutMs = v.coerceIn(500, 8000)) }
    }

    fun setGroupBy(group: GroupBy) {
        _state.update { it.copy(groupBy = group) }
    }

    fun toggleIpsOnly(enabled: Boolean) {
        _state.update { it.copy(showIpsOnly = enabled) }
    }

    fun clearResults() {
        _state.update { it.copy(results = emptyList(), progress = 0f, logs = emptyList(), exportMessage = null) }
    }

    fun dismissExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update {
            it.copy(
                isScanning = false,
                logs = it.logs + "Scan cancelled by user."
            )
        }
    }

    fun exportResults(context: Context) {
        val currentResults = _state.value.results
        if (currentResults.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = "scan_results_${System.currentTimeMillis()}.txt"
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
                    ?: context.filesDir
                val file = File(dir, filename)

                file.bufferedWriter().use { writer ->
                    if (_state.value.showIpsOnly) {
                        currentResults.filter { it.isOpen }.map { it.ip }.distinct().forEach { ip ->
                            writer.write("$ip\n")
                        }
                    } else {
                        writer.write("IP Address,Port,Latency(ms),Organization\n")
                        currentResults.filter { it.isOpen }.forEach { r ->
                            writer.write("${r.ip},${r.port},${r.latencyMs ?: 0},${r.organization ?: "Unknown"}\n")
                        }
                    }
                }

                _state.update {
                    it.copy(exportMessage = "Saved to: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Export failed: ${e.message}")
                }
            }
        }
    }

    fun startScan() {
        val s = _state.value
        if (s.selectedCountry.isBlank()) {
            _state.update { it.copy(error = "Select a country first") }
            return
        }
        if (s.isScanning) return

        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true,
                    progress = 0f,
                    results = emptyList(),
                    error = null,
                    exportMessage = null,
                    logs = listOf("Fetching CIDR blocks for ${s.selectedCountry}...")
                )
            }

            val cidrResult = countryRepo.getCidrBlocks(s.selectedCountry)
            if (cidrResult.isFailure) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        error = "CIDR fetch failed: ${cidrResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }
            val cidrs = cidrResult.getOrThrow()
            if (cidrs.isEmpty()) {
                _state.update { it.copy(isScanning = false, error = "No CIDR blocks returned") }
                return@launch
            }

            val ports = PortPresets.parse(s.portsSpec)
            val targets = sampleTargets(cidrs, s.maxBlocks, s.maxHosts)
            if (targets.isEmpty()) {
                _state.update { it.copy(isScanning = false, error = "No valid targets") }
                return@launch
            }

            val total = targets.size * ports.size
            _state.update {
                it.copy(logs = it.logs + "Scanning ${targets.size} hosts x ${ports.size} ports = $total probes")
            }

            val open = mutableListOf<ScanResult>()
            var done = 0

            try {
                scanEngine.scan(targets, ports, s.timeoutMs).collect { result ->
                    done++
                    if (result.isOpen) {
                        open.add(result)
                        _state.update {
                            it.copy(logs = it.logs + "[+] ${result.ip}:${result.port} open (${result.latencyMs}ms)")
                        }
                    }
                    if (done % 20 == 0 || done == total) {
                        _state.update { it.copy(progress = done.toFloat() / total) }
                    }
                }

                _state.update { it.copy(logs = it.logs + "Enriching ${open.size} results with ASN/ISP...") }
                val enriched = scanRepo.enrichWithAsn(open)

                _state.update {
                    it.copy(
                        isScanning = false,
                        progress = 1f,
                        results = enriched,
                        logs = it.logs + "Done. ${enriched.size} open ports found."
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.update {
                        it.copy(isScanning = false, error = "Scan error: ${e.message}")
                    }
                }
            }
        }
    }
}
