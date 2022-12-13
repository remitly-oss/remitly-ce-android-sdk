package com.remitly.cesdk

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

internal enum class Domain {
    prod,
    staging,
    dev;
}

internal const val REMITLY_PROD_WEB_HOST = "www.remitly.com"
internal const val REMITLY_PROD_API_HOST = "api.remitly.io"
internal const val REMITLY_HELP_CENTER_URL = "https://help.remitly.com"

/**
 * The constructor of this class takes the config data submitted by the host app, merges it with
 * config set in AndroidManifest.xml, then validates it. It throws a RemitlyCEError if data is not
 * valid.
 *
 * The SDK will then use the properties set here.
 */
internal class CEConfigurationInternal(activity: FragmentActivity, config: RemitlyCEConfiguration) {

    // Fetch metadata from host AndroidManifest.xml.
    // The method is deprecated but the new one can't get metadata.
    @Suppress("DEPRECATION")
    private val metaData: Bundle? =
        activity.packageManager.getApplicationInfo(
            activity.packageName,
            PackageManager.GET_META_DATA
        ).metaData
    var deviceEnvironment: CEDeviceEnvironment? = null

    val sdkVersion: String = BuildConfig.VERSION_NAME

    // TODO revert once https://github.com/Remitly/portal/pull/8499 is deployed
    val userAgent = "RemitlyCE/$sdkVersion Android SDK"
    
    var appId: String
    var domain: Domain
    var webHost: String
    var apiHost: String
    var defaultSendCountry: String? = null
    var defaultReceiveCountry: String? = null
    var customerEmail: String? = null
    var languageCode: String
    val startingUrl: HttpUrl
    val redirectCookieValue: String

    // If a frame matches origin in this set then it will have the JS object injected into it
    val allowedOriginRules = setOf("https://remitly.com", "https://*.remitly.com")

    init {
        try {
            // Prop builder is defined further down in this class.
            appId = Prop(config.appId, "APP_ID")
                .merge(metaData)
                .require()
                .build()!!

            defaultSendCountry = Prop(config.defaultSendCountry, "DEFAULT_SEND_COUNTRY")
                .merge(metaData, "USA")
                .validateCountry()
                .build()

            defaultReceiveCountry = Prop(config.defaultReceiveCountry, "DEFAULT_RECEIVE_COUNTRY")
                .merge(metaData, "PHL")
                .validateCountry()
                .build()

            customerEmail = Prop(config.customerEmail, "CUSTOMER_EMAIL")
                .merge(metaData)
                .validateEmail()
                .build()

            languageCode = Prop(config.languageCode, "LANGUAGE_CODE")
                .merge(metaData, Locale.getDefault().language)
                .require()
                .validateLanguage()
                .build()!!

            webHost = Prop(config.webHost, "WEB_HOST")
                .merge(metaData, REMITLY_PROD_WEB_HOST)
                .require()
                .build()!!

            apiHost = Prop(config.apiHost, "API_HOST")
                .merge(metaData, REMITLY_PROD_API_HOST)
                .require()
                .build()!!

            startingUrl = buildStartingUrl()
            redirectCookieValue = buildRedirectCookie()

            domain = when {
                webHost == REMITLY_PROD_WEB_HOST && apiHost == REMITLY_PROD_API_HOST -> Domain.prod
                webHost.contains("preprod") && apiHost.contains("preprod") -> Domain.staging
                else -> Domain.dev
            }

        } catch (ex: Exception) {
            throw RemitlyCEError(ErrorMessage.invalidConfiguration, ex)
        }
    }

    private fun buildStartingUrl(): HttpUrl {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host(webHost)
            .addQueryParameter("utm_medium", "channelpartner")
            .addQueryParameter("utm_source", appId)

        if (defaultSendCountry != null) {
            urlBuilder.addPathSegment(defaultSendCountry!!)
        }
        urlBuilder.addPathSegment(languageCode)
        if (defaultReceiveCountry != null) {
            urlBuilder.addPathSegment(defaultReceiveCountry!!)
        }
        urlBuilder.addPathSegment(appId)
        return urlBuilder.build()
    }

    private fun buildRedirectCookie(): String {
        return "${defaultReceiveCountry ?: "philippines"}/$appId?utm_medium=channelpartner&utm_source=$appId"
    }

    /**
     * Property builder
     */
    private class Prop(prop: String?, val metaDataKey: String) {
        private var result: String? = prop


        // Return prop value.
        fun build(): String? {
            return result
        }

        // Merge the current prop with a key set in the AndroidManifest meta-data.
        fun merge(metaData: Bundle?, default: String? = null) = apply {
            result = result ?: metaData?.getString("com.remitly.cesdk.$metaDataKey") ?: default
        }

        // Ensures the prop is not null or empty, otherwise raises an exception.
        fun require() = apply {
            result = if (result.isNullOrBlank())
                throw Exception("$metaDataKey is missing or invalid")
            else
                result
        }

        fun validateUrl() = apply {
            try {
                result = result?.toHttpUrl().toString()
            } catch (ex: IllegalArgumentException) {
                throw Exception("$metaDataKey is not a valid URL", ex)
            }
        }

        fun validateCountry() = apply {
            if (result !== null) {
                val threeCharactersRegex = "^[A-Z]{3}$".toRegex()
                val ucProp = result!!.uppercase()

                result =
                    if (threeCharactersRegex.matches(ucProp))
                        ucProp
                    else
                        throw Exception("$result is not a valid country code for $metaDataKey")
            }
        }

        fun validateLanguage() = apply {
            if (result !== null) {
                val twoCharactersRegex = "^[a-z]{2}$".toRegex()
                val lcProp = result!!.lowercase()

                result = if (twoCharactersRegex.matches(lcProp))
                    lcProp
                else
                    throw Exception("$result is not a valid language code for $metaDataKey")
            }
        }

        fun validateEmail() = apply {
            if (result !== null) {
                val simpleEmailRegex = "^.+@.+\\..+$".toRegex()

                result = if (simpleEmailRegex.matches(result!!))
                    result
                else
                    throw Exception("$result is not a valid email address for $metaDataKey")
                }
        }

        // Ensure the prop is a valid domain environment string.
        fun validateDomain() = apply {
            result = Domain.values().firstOrNull { it.name == result }?.name
                ?: throw Exception("$result is not a valid domain environment")
        }
    }

}