package com.remitly.cesdk

import android.util.Log
import com.remitly.cesdk.RemitlyCE.Companion.TAG
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

internal class CEHttpClient(val remitly: RemitlyCE) {
    companion object {
        private const val LOGOUT_PATH = "v1/auth/logout"
    }

    val http: OkHttpClient

    init {
        val okHttpBuilder = OkHttpClient.Builder()

        // Log network requests in debug builds
        if (BuildConfig.DEBUG) {
            okHttpBuilder.addInterceptor(
                HttpLoggingInterceptor { Log.v(TAG, it) }
                    .setLevel(HttpLoggingInterceptor.Level.BODY))
        }
        http = okHttpBuilder.build()
    }

    /**
     * Make a request to expire the token in Auth Service.
     */
    fun logout() {
        var apiHost = REMITLY_PROD_API_HOST
        try {
            apiHost = remitly.config.apiHost
        } catch (_: UninitializedPropertyAccessException) {
            Log.w(TAG, "Using default api host")
        }

        val gr = remitly.webView.getCookie("gr")
        val token = remitly.webView.getCookie("token")

        val logoutUrl = HttpUrl.Builder()
            .scheme("https")
            .host(apiHost)
            .addPathSegments(LOGOUT_PATH)
            .build()

        val request = Request.Builder().run {
            url(logoutUrl)
            method("POST", "".toRequestBody())
            token?.let {
                addHeader("Authorization", "Bearer $token")
            }
            gr?.let {
                addHeader("X-Remitly-GlobalRiskPublicId", it)
            }
            remitly.deviceEnvironment.get()?.let {
                addHeader("Remitly-DeviceEnvironmentID", it.id)
            }
            build()
        }
        Log.d(TAG, request.toString())

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                remitly.handleError(RemitlyCEError(ErrorMessage.logoutError, e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful || response.code == 401) {
                        Log.d(TAG, "Logout request successful. $response")
                    } else {
                        remitly.handleError(RemitlyCEError(ErrorMessage.logoutError, IOException(response.toString())))
                    }
                }
            }
        })
    }
}