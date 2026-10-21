package com.countryscanner.app.domain

object PortPresets {
    val COMMON = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445,
        993, 995, 1433, 3306, 3389, 5432, 6379, 8080, 8443, 27017)

    val NAMED: Map<String, List<Int>> = mapOf(
        "web"     to listOf(80, 443, 8000, 8008, 8080, 8081, 8443, 9443),
        "mail"    to listOf(25, 110, 143, 465, 587, 993, 995),
        "db"      to listOf(1433, 1521, 3306, 5432, 6379, 9200, 27017),
        "remote"  to listOf(22, 23, 3389, 5900, 5901),
        "sip"     to listOf(5060, 5061, 5080, 5081),
        "common"  to COMMON
    )

    fun parse(spec: String): List<Int> {
        if (spec.isBlank()) return COMMON
        val ports = mutableSetOf<Int>()
        spec.split(",").map { it.trim().lowercase() }.forEach { part ->
            when {
                part in NAMED -> ports.addAll(NAMED[part]!!)
                part.contains("-") -> {
                    val pieces = part.split("-")
                    if (pieces.size == 2) {
                        val from = pieces[0].toIntOrNull()
                        val to = pieces[1].toIntOrNull()
                        if (from != null && to != null) {
                            val a = minOf(from, to)
                            val b = maxOf(from, to)
                            ports.addAll(a..b)
                        }
                    }
                }
                else -> part.toIntOrNull()?.let { ports.add(it) }
            }
        }
        return ports.filter { it in 1..65535 }.sorted()
    }
}
