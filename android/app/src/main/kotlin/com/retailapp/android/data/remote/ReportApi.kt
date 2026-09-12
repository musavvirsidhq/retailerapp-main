package com.retailapp.android.data.remote

import com.retailapp.android.data.model.DashboardData
import retrofit2.Response
import retrofit2.http.GET

interface ReportApi {
    @GET("api/reports/dashboard")
    suspend fun getDashboard(): Response<DashboardData>
}
