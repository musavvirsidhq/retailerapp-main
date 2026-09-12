package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Company
import com.retailapp.android.data.model.CreateCompanyInput
import com.retailapp.android.data.model.ExtendSubscriptionRequest
import com.retailapp.android.data.model.GrantSubscriptionRequest
import com.retailapp.android.data.model.Subscription
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SuperAdminApi {
    @GET("api/super-admin/companies")
    suspend fun listCompanies(): Response<List<Company>>

    @GET("api/super-admin/companies/{id}")
    suspend fun getCompany(@Path("id") id: Int): Response<Company>

    @POST("api/super-admin/companies")
    suspend fun createCompany(@Body body: CreateCompanyInput): Response<Company>

    @GET("api/super-admin/companies/{id}/subscription")
    suspend fun getSubscriptionHistory(@Path("id") companyId: Int): Response<List<Subscription>>

    @POST("api/super-admin/companies/{id}/trial")
    suspend fun grantTrial(@Path("id") companyId: Int): Response<Subscription>

    @POST("api/super-admin/companies/{id}/subscription")
    suspend fun grantSubscription(@Path("id") companyId: Int, @Body body: GrantSubscriptionRequest): Response<Subscription>

    @POST("api/super-admin/companies/{id}/extend")
    suspend fun extendSubscription(@Path("id") companyId: Int, @Body body: ExtendSubscriptionRequest): Response<Subscription>
}
