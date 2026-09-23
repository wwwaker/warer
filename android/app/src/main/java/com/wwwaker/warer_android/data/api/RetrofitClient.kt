package com.wwwaker.warer_android.data.api

import com.wwwaker.warer_android.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    @Volatile
    private var baseUrl: String = BuildConfig.DEFAULT_API_BASE

    @Volatile
    private var apiService: ApiService? = null

    fun getApiService(): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildService(baseUrl).also { apiService = it }
        }
    }

    fun updateBaseUrl(url: String) {
        synchronized(this) {
            val normalizedUrl = normalizeUrl(url)
            if (normalizedUrl != baseUrl) {
                baseUrl = normalizedUrl
                apiService = buildService(normalizedUrl)
            }
        }
    }

    fun getCurrentBaseUrl(): String = baseUrl

    private fun buildService(url: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
