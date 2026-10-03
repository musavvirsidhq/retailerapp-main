package com.retailapp.android.data.remote

import coil.ImageLoader
import com.retailapp.android.BuildConfig
import com.retailapp.android.RetailApp
import com.retailapp.android.session.PersistentCookieJar
import com.retailapp.android.session.Session
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [BASE_URL] comes from the build type (see app/build.gradle.kts): debug builds may use the
 * plain-HTTP test server, release builds must be HTTPS. Everything else keeps working unchanged.
 */
object NetworkModule {

    val BASE_URL: String = BuildConfig.API_BASE_URL

    val cookieJar by lazy { PersistentCookieJar(RetailApp.instance) }

    // Shared with Coil (see [imageLoader]) so private photo URLs load with the session cookie.
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Release builds log nothing: even BASIC would put URLs (and so customer/bill ids) in logcat.
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            )
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val categoryApi: CategoryApi by lazy { retrofit.create(CategoryApi::class.java) }
    val unitApi: UnitApi by lazy { retrofit.create(UnitApi::class.java) }
    val productApi: ProductApi by lazy { retrofit.create(ProductApi::class.java) }
    val shopApi: ShopApi by lazy { retrofit.create(ShopApi::class.java) }
    val saleApi: SaleApi by lazy { retrofit.create(SaleApi::class.java) }
    val reportApi: ReportApi by lazy { retrofit.create(ReportApi::class.java) }
    val factoryApi: FactoryApi by lazy { retrofit.create(FactoryApi::class.java) }
    val purchaseApi: PurchaseApi by lazy { retrofit.create(PurchaseApi::class.java) }
    val paymentApi: PaymentApi by lazy { retrofit.create(PaymentApi::class.java) }
    val companyApi: CompanyApi by lazy { retrofit.create(CompanyApi::class.java) }
    val superAdminApi: SuperAdminApi by lazy { retrofit.create(SuperAdminApi::class.java) }
    val billApi: BillApi by lazy { retrofit.create(BillApi::class.java) }
    val ledgerApi: LedgerApi by lazy { retrofit.create(LedgerApi::class.java) }
    val attachmentApi: AttachmentApi by lazy { retrofit.create(AttachmentApi::class.java) }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(RetailApp.instance)
            .okHttpClient { okHttpClient }
            .crossfade(true)
            .build()
    }

    /** Turns a server-relative path such as "/api/attachments/3/file" into a full URL. */
    fun absoluteUrl(path: String): String = BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

    /**
     * Runs a Retrofit call and turns it into a [Result] so every ViewModel handles errors the
     * same way, whether it's a non-2xx response (backend errors are plain text bodies, see
     * the internal/handlers Go files' http.Error calls) or a network failure.
     */
    suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> =
        safeCallWithHeaders(block).map { it.first }

    /**
     * Like [safeCall], but also hands back the response headers - the Cycle 5 paged lists read
     * X-Total-Count / X-Total-Amount from them.
     */
    suspend fun <T> safeCallWithHeaders(block: suspend () -> Response<T>): Result<Pair<T, Headers>> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                Result.success(((response.body() ?: Unit) as T) to response.headers())
            } else if (response.code() == 401 && Session.currentUser != null) {
                // The session cookie expired or was revoked. Dropping the user sends RetailAppRoot
                // back to the login screen instead of leaving every screen showing "unauthorized".
                cookieJar.clear()
                Session.currentUser = null
                Result.failure(Exception("Your session has expired. Please log in again."))
            } else {
                val raw = response.errorBody()?.string()?.trim()?.takeIf { it.isNotBlank() }
                val message = when {
                    raw == null -> "Request failed (HTTP ${response.code()})"
                    // Deletes are hard DELETEs; Postgres refuses them for a record that bills or
                    // payments still point at, and its raw message means nothing to a shop user.
                    raw.contains("violates foreign key constraint") ->
                        "It's used by existing bills or payments, so it can't be deleted."
                    // e.g. "forbidden: company admin only" when staff try to archive (Cycle 5).
                    response.code() == 403 && raw.startsWith("forbidden: ") ->
                        "You don't have permission to do this (${raw.removePrefix("forbidden: ")})."
                    else -> raw
                }
                Result.failure(Exception(message))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
