package com.rm.azure.networking

import com.rm.azure.AppConstants.RESOURCE_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceBuilder {
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
    .baseUrl(RESOURCE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .client(client)
    .build()

  fun <T> buildService(service: Class<T>): T {
    return retrofit.create(service)
  }
}