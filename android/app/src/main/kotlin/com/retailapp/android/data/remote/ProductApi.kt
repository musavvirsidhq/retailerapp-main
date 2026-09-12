package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Product
import com.retailapp.android.data.model.ProductInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProductApi {
    @GET("api/products/")
    suspend fun listProducts(): Response<List<Product>>

    @POST("api/products/")
    suspend fun createProduct(@Body body: ProductInput): Response<Product>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Response<Unit>
}
