package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Factory
import com.retailapp.android.data.model.FactoryInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FactoryApi {
    /** Archived suppliers are left out unless [includeArchived] is true (Cycle 5). */
    @GET("api/factories/")
    suspend fun listFactories(@Query("include_archived") includeArchived: Boolean? = null): Response<List<Factory>>

    @GET("api/factories/{id}")
    suspend fun getFactory(@Path("id") id: Int): Response<Factory>

    @POST("api/factories/")
    suspend fun createFactory(@Body body: FactoryInput): Response<Factory>

    @PUT("api/factories/{id}")
    suspend fun updateFactory(@Path("id") id: Int, @Body body: FactoryInput): Response<Factory>

    /** Archives (soft-deletes) the supplier since Cycle 5. Company Admin only. */
    @DELETE("api/factories/{id}")
    suspend fun archiveFactory(@Path("id") id: Int): Response<Unit>

    @POST("api/factories/{id}/restore")
    suspend fun restoreFactory(@Path("id") id: Int): Response<Unit>
}
