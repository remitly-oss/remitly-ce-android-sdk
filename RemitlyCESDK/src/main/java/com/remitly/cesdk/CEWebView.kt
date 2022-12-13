package com.remitly.cesdk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.remitly.cesdk.RemitlyCE.Companion.TAG
import okhttp3.HttpUrl
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

internal class CEWebView(val remitly: RemitlyCE) {
    companion object {
        const val JS_OBJECT_NAME = "cesdk"
    }
    private var myWebView: WebView? = null
    private var context: Context? = null
    private var cookies: Map<String,String>? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(fragmentContext: Context?) {
        context = fragmentContext
        try {
            if (context == null) {
                throw Exception("Couldn't access fragment context")
            }
            myWebView = WebView(context!!)

                myWebView?.run {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = getRemitlyWebViewClient()
                    settings.javaScriptEnabled = true
                    settings.userAgentString = remitly.config.userAgent

                    createJsObject(
                        this,
                        JS_OBJECT_NAME,
                        remitly.config.allowedOriginRules
                    ) { message ->
                        handleMessage(message)
                    }

                    appendLoginRedirectCookie()

                    remitly.fragment.isLoading = true
                    remitly.fragment.isCloseButtonVisible = true
                    remitly.fragment.attachWebView(this)

                    val url = remitly.config.startingUrl.toString()

                    // For ReactNative, WebView must run on the UI thread.
                    myWebView?.post {
                        loadUrl(url)
                    }

                    remitly.eventLogger.logEvent("openWebView", mapOf("url" to url))
                }
        } catch (ex: Exception) {
            remitly.handleError(RemitlyCEError(ErrorMessage.couldntCreateWebView, ex))
        }
    }

    fun destroyWebView() {
        myWebView?.run {
            (this.parent as ViewGroup).removeView(this)
            destroy()
            myWebView = null
        }
    }

    fun canGoBack() = myWebView?.canGoBack() ?: false
    fun goBack() = myWebView?.goBack() ?: Unit
    fun loadUrl(url: String) {
        myWebView?.post {
            loadUrl(url)
        }
    }
    fun currentUrl() = myWebView?.url
    fun getCookie(name: String) = cookies?.get(name)

    fun logout() {
        setCookie("token", null)

        myWebView?.run {
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }
    }

    private fun setCookie(name: String, value: String?) {
        // setCookie may be called before config is set
        var webHost = REMITLY_PROD_WEB_HOST
        try {
            webHost = remitly.config.webHost
        } catch (_: UninitializedPropertyAccessException) {
            Log.w(TAG, "Using default web host")
        }

        val baseUrl = HttpUrl.Builder()
            .scheme("https")
            .host(webHost)
        val cm = CookieManager.getInstance()
        cm.setCookie(baseUrl.toString(), "${name}=${value}; Path=/; Secure")
    }

    private fun parseJsCookies(cookieData: String) {
        if (cookieData.isBlank()) return

        val cookies = getCookieMap(cookieData)
        setDEIDFromCookies(cookies)
    }

    private fun parseWebViewCookies() {
        try {
            val rootUrl = remitly.config.startingUrl.resolve("/").toString()
            val cm = CookieManager.getInstance()
            val cookieString: String? = cm.getCookie(rootUrl)
            if (cookieString == null) {
                return
            }

            val cookieMap = getCookieMap(cookieString)
            setDEIDFromCookies(cookieMap)

            cookies = cookieMap
        } catch (_: Exception) {}
    }

    private fun setDEIDFromCookies(cookies: Map<String,String>) {
        val deId = cookies[CEDeviceEnvironment.DE_ID_KEY]
        val deHash = cookies[CEDeviceEnvironment.DE_HASH_KEY]

        if (!deId.isNullOrEmpty() && !deHash.isNullOrEmpty()) {
            remitly.deviceEnvironment.set(DeviceEnvironmentProps(deId, deHash))
        }
    }

    // From https://stackoverflow.com/a/56068025/100752
    private fun getCookieMap(cookies: String): Map<String,String> {
        val map = mutableMapOf<String,String>()
        val typedArray = cookies.split("; ?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (element in typedArray) {
            val split = element.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if(split.size >= 2) {
                map[split[0]] = split[1]
            } else if(split.size == 1) {
                map[split[0]] = ""
            }
        }
        return map
    }

    // This cookie is needed for Portal to redirect users to the correct landing pages
    private fun appendLoginRedirectCookie() {
        setCookie("ce_login_redirect", remitly.config.redirectCookieValue)
    }

    internal fun loadUrlInBrowser(uri: Uri) {
        Log.v(TAG, "External url requested: $uri")
        val builder = CustomTabsIntent.Builder()
        val intent = builder.build()
        remitly.fragment.context?.let { intent.launchUrl(it, uri) }
    }

    private fun handleMessage(jsonMessage: String) {
        try {
            val message = JSONObject(jsonMessage)
            Log.v(TAG, "Received postMessage: $message")

            when (message.getString("topic")) {
                "exitCE" -> remitly.fragment.dismiss(CEFragment.CloseRequestKind.web)
                "openHelpCenter" -> loadUrlInBrowser(message.getString("payload").toUri())
                "cookies" -> parseJsCookies(message.getString("payload"))
                "hideCloseButton" -> {
                    remitly.fragment.isCloseButtonVisible = false
                }
                "openLoginPage" -> myWebView?.loadUrl(remitly.config.startingUrl.toString())
                "event" -> {
                    remitly.eventHandler.handle(
                        JsMessage(
                            message.getString("topic"),
                            message.getJSONObject("payload")
                        )
                    )
                }
            }

        } catch (err: JSONException) {
            Log.w(TAG, "Failed parsing json postMessage: $jsonMessage", err)
            return
        }
    }

    private fun injectJavaScript() {
        val js = mutableListOf(
            "$JS_OBJECT_NAME.postMessage(JSON.stringify({topic: \"cookies\", payload: document.cookie})); true;",
            "$JS_OBJECT_NAME.config = {};",
        )

        if (remitly.config.customerEmail != null) {
            js.add("$JS_OBJECT_NAME.config.email = \"${remitly.config.customerEmail}\";")
        }

        myWebView?.evaluateJavascript(js.joinToString("\n"), null)
    }

    private fun getRemitlyWebViewClient(): WebViewClient {
        var errorOccurred = false
        var errorOccurredUrl: String? = null

        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request != null) {
                    if(request.url.path != null &&
                        request.url.path!!.contains("/receipt/")) {
                        Log.v(TAG, "Loading receipt with URL ${request.url}")
                        remitly.receiptFragment.setReceiptUrl(receiptUrl = request.url.toString())
                        remitly.receiptFragment.showReceipt()
                        return true
                    }
                    val browserPaths = listOf(
                        "help.remitly.com",
                        "/users/forgot_password",
                        "/home/policy",
                        "/home/privacy_policy",
                        "/home/agreement"
                    )
                    val matchingBrowserPath = browserPaths.firstOrNull() {
                        request.url.toString().contains(it)
                    }
                    if (matchingBrowserPath != null) {
                        loadUrlInBrowser(request.url)
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.v(TAG, "Loading url in webview: $url")

                if(url != errorOccurredUrl) {
                    remitly.fragment.hasErrored = false
                }

                if (!remitly.fragment.isLoading) {
                    remitly.fragment.isLoading = true
                    remitly.fragment.isCloseButtonVisible = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view?.contentHeight == 0) {
                    // If page is empty it might have failed to load, try reloading
                    view.reload()
                } else {
                    super.onPageFinished(view, url)
                }
                Log.v(TAG, "Finished loading url in webview: $url")
                remitly.fragment.isLoading = false
                injectJavaScript()

                parseWebViewCookies()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                handleError(view, request, error, null)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                handleError(view, request, null, errorResponse)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                Log.e(TAG, "Render process crashed, restarting.")
                destroyWebView()
                setupWebView(context)
                return true
            }

            private fun handleError(view: WebView?, request: WebResourceRequest?,
                                    webResourceError: WebResourceError?,
                                    errorResponse: WebResourceResponse?) {
                // Only handle error if it's for the page that we're requesting, not its resources

                if (request!= null && request.isForMainFrame) {
                    errorOccurred = true
                    errorOccurredUrl = request.url.toString()
                    remitly.fragment.isLoading = false
                    remitly.fragment.hasErrored = true

                    webResourceError?.let {
                        val text = if (VERSION.SDK_INT >= VERSION_CODES.M) it.description.toString() else it.toString()
                        remitly.handleError(RemitlyCEError(ErrorMessage.connectionError, IOException(text)))
                    }
                    errorResponse?.let {
                        remitly.handleError(RemitlyCEError(ErrorMessage.serverError, IOException("${it.statusCode} ${it.reasonPhrase}")))
                    }

                    // TODO Retry?
                }
            }
        }
    }
}