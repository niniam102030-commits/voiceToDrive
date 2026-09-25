package com.personal.audioapp.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single Retrofit instance for Google APIs.
 *
 * No OkHttp interceptor is used: the bearer token is passed explicitly through
 * the `Authorization` header of every call, which keeps token refresh logic in
 * one place ([com.personal.audioapp.auth.AuthRepository]).
 *
 * The client gets generous write/read timeouts because a single audio upload
 * can legitimately take minutes on a mobile connection; the stock 10 second
 * timeout would abort it.
 */
object DriveClient {

    private const val BASE_URL = "https://www.googleapis.com/"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: DriveApi by lazy { retrofit.create(DriveApi::class.java) }

    fun bearer(accessToken: String): String =
        if (accessToken.startsWith("Bearer ", ignoreCase = true)) accessToken
        else "Bearer $accessToken"
}
