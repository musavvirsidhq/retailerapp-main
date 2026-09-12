package com.retailapp.android.data.remote

import com.retailapp.android.data.model.BalanceResponse
import com.retailapp.android.data.model.Payment
import com.retailapp.android.data.model.PaymentInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PaymentApi {
    @GET("api/payments/")
    suspend fun listPayments(): Response<List<Payment>>

    @POST("api/payments/")
    suspend fun createPayment(@Body body: PaymentInput): Response<Payment>

    @GET("api/shops/{id}/balance")
    suspend fun getShopBalance(@Path("id") id: Int): Response<BalanceResponse>

    @GET("api/factories/{id}/balance")
    suspend fun getFactoryBalance(@Path("id") id: Int): Response<BalanceResponse>
}
