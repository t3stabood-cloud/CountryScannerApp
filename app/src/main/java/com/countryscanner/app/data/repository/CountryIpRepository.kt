package com.countryscanner.app.data.repository

import com.countryscanner.app.data.remote.CountryIpApi
import com.countryscanner.app.data.remote.CountryIpApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CountryIpRepository(
    private val api: CountryIpApi = CountryIpApiFactory.create()
) {
    suspend fun getCidrBlocks(countryCode: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = api.getCountryBlocks(countryCode.lowercase())
                body.string()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.contains("/") }
                    .toList()
            }
        }
}
