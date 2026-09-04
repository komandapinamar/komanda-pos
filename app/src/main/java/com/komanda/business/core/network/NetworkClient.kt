package com.komanda.business.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

fun interface TokenProvider {
    fun getToken(): String?
}

object NetworkClient {

    fun createAuthInterceptor(tokenProvider: TokenProvider): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")

            if (original.header("Authorization") == null) {
                val token = tokenProvider.getToken()
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            chain.proceed(builder.build())
        }
    }

    fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
        }
    }

    fun createOkHttpClient(
        tokenProvider: TokenProvider = TokenProvider { null }
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor(tokenProvider))
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun create(
        baseUrl: String,
        tokenProvider: TokenProvider = TokenProvider { null }
    ): KomandaApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(createOkHttpClient(tokenProvider))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(KomandaApi::class.java)
    }

    fun create(
        baseUrl: String,
        authToken: String
    ): KomandaApi = create(baseUrl, TokenProvider { authToken })
}
