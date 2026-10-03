package com.retailapp.android.data.remote

import com.retailapp.android.data.model.BalanceResponse
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentDetail
import com.retailapp.android.data.model.PaymentInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PaymentApi {
    /** Cycle 5 filters and paging; [partyType] is "shop" or "factory". */
    @GET("api/payments/")
    suspend fun listPayments(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("party_type") partyType: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<Payment>>

    @POST("api/payments/")
    suspend fun createPayment(@Body body: PaymentInput): Response<Payment>

    @GET("api/payments/{id}")
    suspend fun getPayment(@Path("id") id: Int): Response<PaymentDetail>

    @GET("api/shops/{id}/balance")
    suspend fun getShopBalance(@Path("id") id: Int): Response<BalanceResponse>

    @GET("api/factories/{id}/balance")
    suspend fun getFactoryBalance(@Path("id") id: Int): Response<BalanceResponse>
}
