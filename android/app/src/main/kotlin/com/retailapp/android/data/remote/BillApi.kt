package com.retailapp.android.data.remote

import com.retailapp.android.data.model.BillData
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface BillApi {
    @GET("api/sales/bills/{id}")
    suspend fun getSaleBill(@Path("id") id: Int): Response<BillData>

    @Streaming
    @GET("api/sales/bills/{id}/pdf")
    suspend fun getSalePdf(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/purchases/bills/{id}")
    suspend fun getPurchaseBill(@Path("id") id: Int): Response<BillData>

    @Streaming
    @GET("api/purchases/bills/{id}/pdf")
    suspend fun getPurchasePdf(@Path("id") id: Int): Response<ResponseBody>
}
