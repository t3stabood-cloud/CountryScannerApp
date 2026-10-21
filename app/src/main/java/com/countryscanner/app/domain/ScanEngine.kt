package com.countryscanner.app.domain

import com.countryscanner.app.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

class ScanEngine(
    private val timeoutMs: Int = 1500,
    private val concurrency: Int = 64
) {
    fun scan(ips: List<String>, ports: List<Int>): Flow<ScanResult> = channelFlow {
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            ips.forEach { ip ->
                ports.forEach { port ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            trySend(probe(ip, port))
                        }
                    }
                }
            }
        }
        awaitClose { }
    }

    private fun probe(ip: String, port: Int): ScanResult {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                ScanResult(ip, port, true, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            ScanResult(ip, port, false)
        }
    }
}
