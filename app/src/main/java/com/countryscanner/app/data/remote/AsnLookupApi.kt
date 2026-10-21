package com.countryscanner.app.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class IpWhoIsResponse(
    @SerializedName("ip") val ip: String?,
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("connection") val connection: ConnectionInfo?
)

data class ConnectionInfo(
    @SerializedName("asn") val asn: Int?,
    @SerializedName("org") val org: String?,
    @SerializedName("isp") val isp: String?
)

interface AsnLookupApi {
    @GET("{ip}")
    suspend fun lookup(@Path("ip") ip: String): IpWhoIsResponse
}

object AsnLookupApiFactory {
    fun create(): AsnLookupApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://ipwho.is/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AsnLookupApi::class.java)
    }
}
