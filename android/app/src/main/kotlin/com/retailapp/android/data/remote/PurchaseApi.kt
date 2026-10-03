package com.retailapp.android.data.remote

import com.retailapp.android.data.model.CancelRequest
import com.retailapp.android.data.model.Purchase
import com.retailapp.android.data.model.PurchaseInput
import com.retailapp.android.data.model.PurchaseItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PurchaseApi {
    /** Same optional filters and paging as SaleApi.listSales. */
    @GET("api/purchases/")
    suspend fun listPurchases(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<Purchase>>

    @GET("api/purchases/{id}/items")
    suspend fun getPurchaseItems(@Path("id") id: Int): Response<List<PurchaseItem>>

    @POST("api/purchases/")
    suspend fun createPurchase(@Body body: PurchaseInput): Response<Purchase>

    @POST("api/purchases/bills/{id}/cancel")
    suspend fun cancelPurchase(@Path("id") id: Int, @Body body: CancelRequest): Response<Purchase>
}
