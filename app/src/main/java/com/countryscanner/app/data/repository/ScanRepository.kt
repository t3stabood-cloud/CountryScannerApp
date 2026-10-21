package com.countryscanner.app.data.repository

import com.countryscanner.app.data.model.ScanResult
import com.countryscanner.app.data.remote.AsnLookupApi
import com.countryscanner.app.data.remote.AsnLookupApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class ScanRepository(
    private val asnApi: AsnLookupApi = AsnLookupApiFactory.create()
) {
    suspend fun enrichWithAsn(results: List<ScanResult>): List<ScanResult> {
        if (results.isEmpty()) return results
        val uniqueIps = results.map { it.ip }.distinct()
        val orgMap = mutableMapOf<String, Pair<String?, Int?>>()

        withContext(Dispatchers.IO) {
            uniqueIps.chunked(20).forEach { chunk ->
                coroutineScope {
                    chunk.map { ip ->
                        async {
                            runCatching { asnApi.lookup(ip) }
                                .getOrNull()
                                ?.takeIf { it.success }
                                ?.connection
                                ?.let { conn ->
                                    val name = conn.org ?: conn.isp
                                    if (name != null) {
                                        synchronized(orgMap) { orgMap[ip] = name to conn.asn }
                                    }
                                }
                        }
                    }.awaitAll()
                }
            }
        }

        return results.map { r ->
            val (org, asn) = orgMap[r.ip] ?: (null to null)
            r.copy(organization = org, asn = asn)
        }
    }
}
