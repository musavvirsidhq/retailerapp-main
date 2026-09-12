package com.retailapp.android.data.remote

import com.retailapp.android.RetailApp
import com.retailapp.android.session.PersistentCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Single place to point the app at a different backend: change [BASE_URL] (and the allowed
 * host in res/xml/network_security_config.xml if it's still plain HTTP) and everything else
 * keeps working unchanged.
 */
object NetworkModule {

    const val BASE_URL = "http://13.215.157.19/"

    val cookieJar by lazy { PersistentCookieJar(RetailApp.instance) }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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

    /**
     * Runs a Retrofit call and turns it into a [Result] so every ViewModel handles errors the
     * same way, whether it's a non-2xx response (backend errors are plain text bodies, see
     * the internal/handlers Go files' http.Error calls) or a network failure.
     */
    suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                Result.success((response.body() ?: Unit) as T)
            } else {
                val message = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "Request failed (HTTP ${response.code()})"
                Result.failure(Exception(message))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
