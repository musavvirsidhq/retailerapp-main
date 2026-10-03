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
    /** Archived products are left out unless [includeArchived] is true (Cycle 5). */
    @GET("api/products/")
    suspend fun listProducts(@Query("include_archived") includeArchived: Boolean? = null): Response<List<Product>>

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): Response<Product>

    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: Int, @Body body: ProductInput): Response<Product>

    @POST("api/products/")
    suspend fun createProduct(@Body body: ProductInput): Response<Product>

    /** Archives (soft-deletes) the product since Cycle 5. Company Admin only. */
    @DELETE("api/products/{id}")
    suspend fun archiveProduct(@Path("id") id: Int): Response<Unit>

    @POST("api/products/{id}/restore")
    suspend fun restoreProduct(@Path("id") id: Int): Response<Unit>

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
