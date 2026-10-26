package com.countryscanner.app.domain

import com.countryscanner.app.data.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class ScanEngine(
    concurrency: Int = 128
) {
    private val workerCount = concurrency.also {
        require(it > 0) { "concurrency must be greater than zero" }
    }

    fun scan(
        ips: List<String>,
        ports: List<Int>,
        timeoutMs: Int = 2000
    ): Flow<ScanResult> = channelFlow {
        val jobs = Channel<Pair<String, Int>>(capacity = workerCount)

        coroutineScope {
            launch {
                try {
                    for (ip in ips) {
                        for (port in ports) {
                            jobs.send(ip to port)
                        }
                    }
                } finally {
                    jobs.close()
                }
            }

            repeat(workerCount) {
                launch(Dispatchers.IO) {
                    for ((ip, port) in jobs) {
                        send(probe(ip, port, timeoutMs))
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
                ScanResult(
                    ip,
                    port,
                    true,
                    System.currentTimeMillis() - start
                )
            }
        } catch (e: Exception) {
            ScanResult(ip, port, false)
        }
    }
}
