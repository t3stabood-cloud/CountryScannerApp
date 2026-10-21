package com.countryscanner.app.data.model

data class ScanResult(
    val ip: String,
    val port: Int,
    val isOpen: Boolean,
    val latencyMs: Long? = null,
    val organization: String? = null,
    val asn: Int? = null
)
