package com.rm.azure.networking

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface AppEndpoints {

  @GET("api/GetSecret")
  fun getSecretFromMiddleware(
    @Header("Authorization") accessToken: String,
    @Query("keyName") key: String
  ): Call<ResponseBody>
}