package com.rm.azure.networking

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceBuilder {
  private const val BASE_URL = "https://keyvaultmiddleware.azurewebsites.net/"

  private val interceptor = run {
    val httpLoggingInterceptor = HttpLoggingInterceptor()
    httpLoggingInterceptor.apply {
      httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    }
  }
  private val client = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build()

  private val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .client(client)
    .build()

  fun <T> buildService(service: Class<T>): T {
    return retrofit.create(service)
  }
}