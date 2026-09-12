package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.FactoryInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FactoryApi {
    @GET("api/factories/")
    suspend fun listFactories(): Response<List<Factory>>

    @POST("api/factories/")
    suspend fun createFactory(@Body body: FactoryInput): Response<Factory>

    @DELETE("api/factories/{id}")
    suspend fun deleteFactory(@Path("id") id: Int): Response<Unit>
}
