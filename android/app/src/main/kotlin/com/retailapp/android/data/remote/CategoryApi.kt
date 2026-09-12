package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Category
import com.retailapp.android.data.model.CreateCategoryRequest
import com.retailapp.android.data.model.CreateSubcategoryRequest
import com.retailapp.android.data.model.Subcategory
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CategoryApi {
    @GET("api/categories")
    suspend fun listCategories(): Response<List<Category>>

    @POST("api/categories/")
    suspend fun createCategory(@Body body: CreateCategoryRequest): Response<Category>

    @GET("api/categories/{id}/subcategories")
    suspend fun listSubcategories(@Path("id") categoryId: Int): Response<List<Subcategory>>

    @POST("api/categories/{id}/subcategories")
    suspend fun createSubcategory(
        @Path("id") categoryId: Int,
        @Body body: CreateSubcategoryRequest,
    ): Response<Subcategory>
}
