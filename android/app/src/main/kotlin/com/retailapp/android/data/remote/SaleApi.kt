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
import retrofit2.http.Query

interface SaleApi {
    /**
     * Cycle 5 filters and paging. All optional; with [limit] set a current backend adds
     * X-Total-Count / X-Total-Amount headers (an older one ignores the params - see PagedList).
     */
    @GET("api/sales/")
    suspend fun listSales(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<Sale>>

    @GET("api/sales/{id}/items")
    suspend fun getSaleItems(@Path("id") id: Int): Response<List<SaleItem>>

    @POST("api/sales/")
    suspend fun createSale(@Body body: SaleInput): Response<Sale>

    @POST("api/sales/bills/{id}/cancel")
    suspend fun cancelSale(@Path("id") id: Int, @Body body: CancelRequest): Response<Sale>
}
