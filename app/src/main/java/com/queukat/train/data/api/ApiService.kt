// ApiService.kt
package com.queukat.train.data.api

import com.queukat.train.data.model.RoutesResponse
import com.queukat.train.data.model.StopDto
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("api/stops")
    fun getStops(): Call<List<StopDto>>

    @GET("api/routes")
    fun getRoutes(
        @Query("start") start: String,
        @Query("finish") finish: String,
        @Query("date") date: String
    ): Call<RoutesResponse>

    /**
     *   – ё «cumulative» JSON   
     */
    @GET("api/routes/cumulative")
    fun getCumulativeRoutes(): Call<ResponseBody>
}
