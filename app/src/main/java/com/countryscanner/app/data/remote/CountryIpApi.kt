package com.countryscanner.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface CountryIpApi {
    @GET("{cc}-aggregated.zone")
    suspend fun getCountryBlocks(@Path("cc") countryCode: String): ResponseBody
}

object CountryIpApiFactory {
    fun create(): CountryIpApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
        return Retrofit.Builder()
            .baseUrl("https://www.ipdeny.com/ipblocks/data/aggregated/")
            .client(client)
            .build()
            .create(CountryIpApi::class.java)
    }
}
