package com.countryscanner.app.domain

import com.countryscanner.app.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

class ScanEngine(
    private val concurrency: Int = 128
) {
    fun scan(ips: List<String>, ports: List<Int>, timeoutMs: Int = 2000): Flow<ScanResult> = channelFlow {
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            ips.forEach { ip ->
                ports.forEach { port ->
                    coroutineContext.ensureActive()
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            send(probe(ip, port, timeoutMs))
                        }
                    }
                }
            }
        }
    }

    private fun probe(ip: String, port: Int, timeoutMs: Int): ScanResult {
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
