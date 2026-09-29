package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import com.retailapp.android.data.model.QuickItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApi {
    @GET("api/products/")
    suspend fun listProducts(): Response<List<Product>>

    @POST("api/products/")
    suspend fun createProduct(@Body body: ProductInput): Response<Product>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Response<Unit>

    /** Pinned items first, then the most-used items. [shopId] switches to "usually buys". */
    @GET("api/products/frequent")
    suspend fun frequentItems(
        @Query("type") type: String,
        @Query("shop_id") shopId: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<QuickItem>>

    @PUT("api/products/{id}/pin")
    suspend fun pin(@Path("id") id: Int): Response<Unit>

    @DELETE("api/products/{id}/pin")
    suspend fun unpin(@Path("id") id: Int): Response<Unit>
}
