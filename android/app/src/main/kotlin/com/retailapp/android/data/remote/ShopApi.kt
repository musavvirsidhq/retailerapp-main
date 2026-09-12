package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Shop
import com.retailapp.android.data.model.ShopInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ShopApi {
    @GET("api/shops/")
    suspend fun listShops(): Response<List<Shop>>

    @POST("api/shops/")
    suspend fun createShop(@Body body: ShopInput): Response<Shop>

    @DELETE("api/shops/{id}")
    suspend fun deleteShop(@Path("id") id: Int): Response<Unit>
}
