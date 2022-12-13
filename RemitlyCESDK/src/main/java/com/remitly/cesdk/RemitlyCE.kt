package com.remitly.cesdk

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity

// Make RemitlyCE instance available through context.
private lateinit var rmtly: RemitlyCE
internal var Context?.remitly: RemitlyCE
    get() {
        return rmtly
    }
    set(value) {
        rmtly = value
    }

/**
 * The public class for the SDK.
 * Host app can extended it to provide handlers like onError etc.
 *
 * The other internal classes in this SDK are initialized with a reference to the RemitlyCE
 * instance, and can access each other through its properties.
 */
open class RemitlyCE {

    internal companion object {
        @JvmField
        val TAG = "RemitlyCE"
    }

    @JvmField
    internal var hostActivity: FragmentActivity? = null
    internal lateinit var config: CEConfigurationInternal

    internal val fragment: CEFragment by lazy { CEFragment() }
    internal val webView: CEWebView by lazy { CEWebView(this) }
    internal val receiptFragment: CEReceiptFragment by lazy { CEReceiptFragment(this) }
    internal val eventHandler: CEEventHandler by lazy { CEEventHandler(this) }
    internal val deviceEnvironment: CEDeviceEnvironment by lazy { CEDeviceEnvironment(this) }
    internal val httpClient: CEHttpClient by lazy { CEHttpClient(this) }
    internal val eventLogger: CEEventLogger by lazy {
        CEEventLogger.rootLogger(this, httpClient.http)
    }

    /**
     * Validates the config in AndroidManifest file and initializes the SDK.
     * Must be called prior to presenting the Remitly UX.
     */
    fun loadConfig(activity: FragmentActivity): Boolean {
        return loadConfig(activity, null)
    }

    /**
     * Accepts a [RemitlyCEConfiguration], validates it and the AndroidManifest file config,
     * and initializes the SDK. Must be called prior to presenting the Remitly UX.
     */
    fun loadConfig(activity: FragmentActivity, userConfig: RemitlyCEConfiguration?): Boolean {
        hostActivity = activity
        try {
            val configuration = userConfig ?: RemitlyCEConfiguration.Builder().build()
            config = CEConfigurationInternal(activity, configuration)
            config.deviceEnvironment = deviceEnvironment
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to configure the RemitlyCE SDK", ex)
            handleError(IllegalArgumentException(ex))
            return false
        }
        return true
    }

    /**
     * Launch the Remitly UX atop your app in a full-screen modal.
     */
    fun present(): Boolean {
        if (!this::config.isInitialized) {
            handleError(IllegalArgumentException(ErrorMessage.notInitialized.name))
            return false
        }

        try {
            Log.d(TAG, "Launching!")
            fragment.context.remitly = this
            fragment.handlePresent()
        } catch (ex: Throwable) {
            Log.e(TAG, "Error launching fragment")
            handleError(ex)
            return false
        }
        return true
    }

    /**
     * An idempotent API to hide any presented Remitly UX.
     */
    fun dismiss(): Boolean {
        return try {
            Log.d(TAG, "Dismissing")

            if (fragment.isAdded) {
                fragment.dismiss()
            }
            true
        } catch (ex: Throwable) {
            handleError(RemitlyCEError(ErrorMessage.dismissError, ex))
            false
        }
    }

    /**
     * An idempotent API to log the user out of Remitly.
     * This must be called whenever the user logs out of your app.
     */
    fun logout(): Boolean {
        return try {
            Log.d(TAG, "Logging out")

            // Invalidate auth token
            httpClient.logout()

            // Clear cookies
            webView.logout()

            true
        } catch (ex: Throwable) {
            handleError(RemitlyCEError(ErrorMessage.logoutError, ex))
            false
        }
    }

    // User-overrideable handlers
    /**
     * Heartbeat event called frequently as the user interacts with the
     * Remitly UI.
     */
    open fun onUserActivity() {
        Log.d(TAG, "Default onUserActivity handler called")
    }

    /**
     * Triggered when the user successfully submits a transaction request.
     */
    open fun onTransferSubmitted() {
        Log.d(TAG, "Default onTransferSubmitted handler called")
    }

    /**
     * Called when there is an error presenting the UX or
     * when the user encounters an error in the UI.
     */
    open fun onError(error: Throwable) {
        Log.d(TAG, "Default onError handler called with", error)
    }

    /**
     * Called when the Remitly UI is closed.
     */
    open fun onDismissed() {
        Log.d(TAG, "Default onDismissed handler called")
    }

    // Internal handler
    internal fun handleError(ex: Throwable) {
        Log.e(TAG, "SDK Error", ex)
        onError(ex)
    }
}

