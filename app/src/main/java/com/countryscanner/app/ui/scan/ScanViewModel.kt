package com.countryscanner.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countryscanner.app.data.model.ScanResult
import com.countryscanner.app.data.repository.CountryIpRepository
import com.countryscanner.app.data.repository.ScanRepository
import com.countryscanner.app.domain.PortPresets
import com.countryscanner.app.domain.ScanEngine
import com.countryscanner.app.util.sampleTargets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GroupBy { PORT, ORGANIZATION }

data class ScanUiState(
    val selectedCountry: String = "",
    val portsSpec: String = "common",
    val maxBlocks: Int = 3,
    val maxHosts: Int = 32,
    val isLoadingCidrs: Boolean = false,
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val logs: List<String> = emptyList(),
    val results: List<ScanResult> = emptyList(),
    val groupBy: GroupBy = GroupBy.PORT,
    val error: String? = null
)

class ScanViewModel(
    private val countryRepo: CountryIpRepository = CountryIpRepository(),
    private val scanRepo: ScanRepository = ScanRepository(),
    private val scanEngine: ScanEngine = ScanEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onCountrySelected(code: String) {
        _state.update { it.copy(selectedCountry = code, error = null) }
    }

    fun onPortsSpecChanged(spec: String) {
        _state.update { it.copy(portsSpec = spec) }
    }

    fun setGroupBy(group: GroupBy) {
        _state.update { it.copy(groupBy = group) }
    }

    fun clearResults() {
        _state.update { it.copy(results = emptyList(), progress = 0f, logs = emptyList()) }
    }

    fun startScan() {
        val s = _state.value
        if (s.selectedCountry.isBlank()) {
            _state.update { it.copy(error = "Select a country first") }
            return
        }
        if (s.isScanning) return

        viewModelScope.launch {
            _state.update {
                it.copy(isScanning = true, progress = 0f, results = emptyList(),
                    error = null,
                    logs = listOf("Fetching CIDR blocks for ${s.selectedCountry}..."))
            }

            val cidrResult = countryRepo.getCidrBlocks(s.selectedCountry)
            if (cidrResult.isFailure) {
                _state.update {
                    it.copy(isScanning = false,
                        error = "CIDR fetch failed: ${cidrResult.exceptionOrNull()?.message}")
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

            scanEngine.scan(targets, ports).collect { result ->
                done++
                if (result.isOpen) {
                    open.add(result)
                    _state.update {
                        it.copy(logs = it.logs + "[+] ${result.ip}:${result.port} open (${result.latencyMs}ms)")
                    }
                }
                if (done % 10 == 0 || done == total) {
                    _state.update { it.copy(progress = done.toFloat() / total) }
                }
            }

            _state.update { it.copy(logs = it.logs + "Enriching ${open.size} results with ASN/ISP...") }
            val enriched = scanRepo.enrichWithAsn(open)

            _state.update {
                it.copy(isScanning = false, progress = 1f, results = enriched,
                    logs = it.logs + "Done. ${enriched.size} open ports found.")
            }
        }
    }
}
