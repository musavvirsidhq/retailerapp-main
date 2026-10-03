package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.model.ShopInput
import com.retailapp.android.data.model.ShopUpdate
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ShopApi {
    /** Archived customers are left out unless [includeArchived] is true (Cycle 5). */
    @GET("api/shops/")
    suspend fun listShops(@Query("include_archived") includeArchived: Boolean? = null): Response<List<Shop>>

    @GET("api/shops/{id}")
    suspend fun getShop(@Path("id") id: Int): Response<Shop>

    @POST("api/shops/")
    suspend fun createShop(@Body body: ShopInput): Response<Shop>

    @PUT("api/shops/{id}")
    suspend fun updateShop(@Path("id") id: Int, @Body body: ShopUpdate): Response<Shop>

    /** Archives (soft-deletes) the customer since Cycle 5. Company Admin only. */
    @DELETE("api/shops/{id}")
    suspend fun archiveShop(@Path("id") id: Int): Response<Unit>

    @POST("api/shops/{id}/restore")
    suspend fun restoreShop(@Path("id") id: Int): Response<Unit>
}
