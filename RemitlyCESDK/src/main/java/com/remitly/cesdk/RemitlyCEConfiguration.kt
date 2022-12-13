package com.remitly.cesdk

/**
 * Configuration options to initialize RemitlyCE.
 *
 * @sample RemitlyCEConfiguration.Builder
 * @see Builder
 */
class RemitlyCEConfiguration private constructor(
    internal val appId: String?,
    internal val defaultSendCountry: String?,
    internal val defaultReceiveCountry: String?,
    internal val customerEmail: String?,
    internal val languageCode: String?,
    internal val webHost: String?, // remitly-internal-use
    internal val apiHost: String?, // remitly-internal-use
) {

    private constructor(builder: Builder) : this(
        builder.appId,
        builder.defaultSendCountry,
        builder.defaultReceiveCountry,
        builder.customerEmail,
        builder.languageCode,
        builder.webHost,
        builder.apiHost,
    )

    companion object {
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        /**
         * Unique identifier for your app, provided by Remitly.
         */
        var appId: String? = null

        /**
         * Three letter country code (ISO 3166-1 alpha-3).
         * (Optional)
         */
        var defaultSendCountry: String? = null

        /**
         * Three letter country code (ISO 3166-1 alpha-3).
         * (Optional)
         */
        var defaultReceiveCountry: String? = null

        /**
         * Customer's email address to prefill on login form.
         * (Optional)
         */
        var customerEmail: String? = null

        /**
         * Provide a two-letter language code (ISO 639-1) to override system language.
         * (Optional)
         */
        var languageCode: String? = null

        /**
         * remitly-internal-use
         * @suppress
         */
        var webHost: String? = null

        /**
         * remitly-internal-use
         * @suppress
         */
        var apiHost: String? = null

        fun build() = RemitlyCEConfiguration(this)
    }
}