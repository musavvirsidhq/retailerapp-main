package com.retailapp.android.data.remote

import com.retailapp.android.data.model.UnitDto
import retrofit2.Response
import retrofit2.http.GET

interface UnitApi {
    @GET("api/units")
    suspend fun listUnits(): Response<List<UnitDto>>
}
