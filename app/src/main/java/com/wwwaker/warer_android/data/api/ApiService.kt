package com.wwwaker.warer_android.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("v1/compute")
    suspend fun compute(@Body request: ComputeRequest): ComputeResponse

    @GET("health")
    suspend fun healthCheck(): HealthResponse
}
