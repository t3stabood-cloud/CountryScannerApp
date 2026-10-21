package com.countryscanner.app.util

import org.apache.commons.net.util.SubnetUtils
import kotlin.random.Random

fun sampleTargets(
    cidrs: List<String>,
    maxBlocks: Int,
    maxHosts: Int,
    random: Random = Random.Default
): List<String> {
    val usable = cidrs.mapNotNull { cidr ->
        runCatching {
            val subnet = SubnetUtils(cidr).apply { isInclusiveHostCount = true }
            val size = subnet.info.allAddresses.size
            if (size in 256..65536) cidr else null
        }.getOrNull()
    }
    if (usable.isEmpty()) return emptyList()

    val selected = usable.shuffled(random).take(maxBlocks)
    val targets = mutableListOf<String>()

    selected.forEach { cidr ->
        val subnet = SubnetUtils(cidr).apply { isInclusiveHostCount = true }
        val all = subnet.info.allAddresses.toList()
        if (all.size <= maxHosts) targets.addAll(all)
        else targets.addAll(all.shuffled(random).take(maxHosts))
    }

    return targets
}
