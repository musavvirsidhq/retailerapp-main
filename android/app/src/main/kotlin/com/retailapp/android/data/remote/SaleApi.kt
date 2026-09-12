package com.retailapp.android.data.remote

import com.retailapp.android.data.model.CancelRequest
import com.retailapp.android.data.model.Sale
import com.retailapp.android.data.model.SaleInput
import com.retailapp.android.data.model.SaleItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SaleApi {
    @GET("api/sales/")
    suspend fun listSales(): Response<List<Sale>>

    @GET("api/sales/{id}/items")
    suspend fun getSaleItems(@Path("id") id: Int): Response<List<SaleItem>>

    @POST("api/sales/")
    suspend fun createSale(@Body body: SaleInput): Response<Sale>

    @POST("api/sales/bills/{id}/cancel")
    suspend fun cancelSale(@Path("id") id: Int, @Body body: CancelRequest): Response<Sale>
}
