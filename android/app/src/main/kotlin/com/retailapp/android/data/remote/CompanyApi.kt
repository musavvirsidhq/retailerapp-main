package com.retailapp.android.data.remote

import com.retailapp.android.data.model.CompanyUser
import com.retailapp.android.data.model.CreateStaffInput
import com.retailapp.android.data.model.SubscriptionStatus
import com.retailapp.android.data.model.UpdatePermissionsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CompanyApi {
    @GET("api/company/users")
    suspend fun listUsers(): Response<List<CompanyUser>>

    @POST("api/company/users")
    suspend fun createStaff(@Body body: CreateStaffInput): Response<CompanyUser>

    @PUT("api/company/users/{id}/permissions")
    suspend fun updatePermissions(@Path("id") id: Int, @Body body: UpdatePermissionsRequest): Response<CompanyUser>

    @PUT("api/company/users/{id}/disable")
    suspend fun disableUser(@Path("id") id: Int): Response<CompanyUser>

    @GET("api/company/subscription-status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatus>
}
