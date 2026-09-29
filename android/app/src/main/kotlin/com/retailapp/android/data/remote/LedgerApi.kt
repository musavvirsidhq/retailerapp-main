package com.retailapp.android.data.remote

import com.retailapp.android.data.model.Attachment
import com.retailapp.android.data.model.DeleteAttachmentRequest
import com.retailapp.android.data.model.DuesResponse
import com.retailapp.android.data.model.LedgerResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface LedgerApi {
    @GET("api/reports/customer-dues")
    suspend fun customerDues(
        @Query("sort") sort: String,
        @Query("include_zero") includeZero: Boolean,
    ): Response<DuesResponse>

    @GET("api/reports/supplier-dues")
    suspend fun supplierDues(
        @Query("sort") sort: String,
        @Query("include_zero") includeZero: Boolean,
    ): Response<DuesResponse>

    @GET("api/shops/{id}/ledger")
    suspend fun customerLedger(
        @Path("id") id: Int,
        @Query("from") from: String?,
        @Query("type") type: String?,
        @Query("with_photos") withPhotos: Boolean,
    ): Response<LedgerResponse>

    @GET("api/factories/{id}/ledger")
    suspend fun supplierLedger(
        @Path("id") id: Int,
        @Query("from") from: String?,
        @Query("type") type: String?,
        @Query("with_photos") withPhotos: Boolean,
    ): Response<LedgerResponse>
}

/** Which record a photo belongs to; [path] is its segment in /api/{path}/{id}/attachments. */
enum class AttachmentEntity(val path: String) {
    SALE("sales"),
    PURCHASE("purchases"),
    PAYMENT("payments"),
}

interface AttachmentApi {
    @GET("api/{entity}/{id}/attachments")
    suspend fun list(@Path("entity") entity: String, @Path("id") id: Int): Response<List<Attachment>>

    @Multipart
    @POST("api/{entity}/{id}/attachments")
    suspend fun upload(
        @Path("entity") entity: String,
        @Path("id") id: Int,
        @Part file: MultipartBody.Part,
    ): Response<Attachment>

    @HTTP(method = "DELETE", path = "api/{entity}/{id}/attachments/{attachmentId}", hasBody = true)
    suspend fun delete(
        @Path("entity") entity: String,
        @Path("id") id: Int,
        @Path("attachmentId") attachmentId: Int,
        @Body body: DeleteAttachmentRequest,
    ): Response<Unit>
}
