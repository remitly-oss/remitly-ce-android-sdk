package com.remitly.cesdk

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.*
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.remitly.cesdk.RemitlyCE.Companion.TAG
import org.json.JSONObject
import java.io.IOException

internal class CEReceiptFragment(val remitly: RemitlyCE) : DialogFragment() {

    private lateinit var myView: View
    private var receiptUrl: String? = null
    private var myWebView: WebView? = null
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private var isLargeLayout = false

    enum class CloseRequestKind(string: String) {
        native("modal_close_button_pressed"),
    }

    var isLoading = true
        set(value) {
            field = value

            // Spinner
            myView.findViewById<CircularProgressIndicator>(R.id.ce_receipt_spinner)?.let {
                if (value) it.show() else it.hide()
            }
        }

    var isCloseButtonVisible = true
        set(value) {
            field = value

            // Close button
            myView.findViewById<ImageButton>(R.id.ce_receipt_close)?.visibility =
                if (value) View.VISIBLE else View.INVISIBLE
        }

    var hasErrored = false
        set(value) {
            field = value

            // Error screen
            myView.findViewById<LinearLayoutCompat>(R.id.ce_error)?.visibility =
                if (value) View.VISIBLE else View.INVISIBLE
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        myView = inflater.inflate(R.layout.ce_receipt_fragment, container, false)
        isLargeLayout = resources.getBoolean(R.bool.large_layout)

        setupWebView()
        setupCloseButton()
        setupErrorButton()

        return myView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        myWebView = myView.findViewById(R.id.ce_receipt_web_view)
        myWebView?.settings?.javaScriptEnabled = true
        myWebView?.settings?.userAgentString = remitly.config.userAgent
        myWebView?.webViewClient = getRemitlyWebViewClient()
        if (this.receiptUrl != null) {
            myWebView?.loadUrl(this.receiptUrl!!)
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (this::onBackPressedCallback.isInitialized) {
            onBackPressedCallback.isEnabled = true
        }
    }

    // Overriding onCreateDialog and onStart so the fragment can be full-screen,
    // not showing the action bar.
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        remitly.onDismissed()
    }

    private fun setupCloseButton() {
        val closeButton: View? = myView.findViewById<ImageButton>(R.id.ce_receipt_close)
        closeButton?.setOnClickListener { handleClose(CloseRequestKind.native) }
    }

    private fun setupErrorButton() {
        val errorButton: View? = myView.findViewById<ImageButton>(R.id.ce_error_button)
        errorButton?.setOnClickListener {
            loadUrlInBrowser(
                Uri.parse(
                    REMITLY_HELP_CENTER_URL
                )
            )
        }
    }

    internal fun showReceipt() {
        remitly.hostActivity?.let {
            val transaction = it.supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                    androidx.appcompat.R.anim.abc_grow_fade_in_from_bottom,
                    androidx.appcompat.R.anim.abc_shrink_fade_out_from_bottom,
                    androidx.appcompat.R.anim.abc_grow_fade_in_from_bottom,
                    androidx.appcompat.R.anim.abc_shrink_fade_out_from_bottom,
                )
            this.show(transaction, TAG)
        }
    }

    internal fun handleClose(closeRequestKind: CloseRequestKind) {
        var lastUrl: String? = null
        try {
            lastUrl = remitly.webView.currentUrl()
            myWebView?.loadUrl("about:blank")
            destroyWebView()
        } catch (_: Exception) {
            // ignore
        }

        val payload = JSONObject().put("eventType", "close")
        remitly.eventHandler.handle(JsMessage("event", payload))
        remitly.eventLogger.logEvent(
            "closeWebView",
            mapOf(
                "url" to lastUrl,
                "closeReason" to closeRequestKind
            )
        )

        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                androidx.appcompat.R.anim.abc_grow_fade_in_from_bottom,
                androidx.appcompat.R.anim.abc_shrink_fade_out_from_bottom,
                androidx.appcompat.R.anim.abc_grow_fade_in_from_bottom,
                androidx.appcompat.R.anim.abc_shrink_fade_out_from_bottom,
            )
            .remove(this)
            .commit()
    }

    fun destroyWebView() {
        myWebView?.run {
            (this.parent as ViewGroup).removeView(this)
            destroy()
            myWebView = null
        }
    }

    fun setReceiptUrl(receiptUrl: String) {
        this.receiptUrl = receiptUrl
    }

    internal fun loadUrlInBrowser(uri: Uri) {
        Log.v(TAG, "External url requested: $uri")
        val builder = CustomTabsIntent.Builder()
        val intent = builder.build()
        remitly.receiptFragment.context?.let { intent.launchUrl(it, uri) }
    }

    private fun getRemitlyWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request != null) {
                    // This is our web site, so do not override; let our WebView load the page
                    if (request.url.host == remitly.config.webHost) {
                        return false
                    }

                    // Otherwise, the link is not for a page on our site, so launch Chrome custom tab to handle URL
                    loadUrlInBrowser(request.url)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.v(TAG, "Loading url in webview: $url")

                remitly.receiptFragment.hasErrored = false
                if (!remitly.receiptFragment.isLoading) {
                    remitly.receiptFragment.isLoading = true
                    remitly.receiptFragment.isCloseButtonVisible = true
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
                remitly.receiptFragment.isLoading = false
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
                return true
            }

            private fun handleError(
                view: WebView?,
                request: WebResourceRequest?,
                webResourceError: WebResourceError?,
                errorResponse: WebResourceResponse?
            ) {
                // Only handle error if it's for the page that we're requesting, not its resources
                if (view?.url == request?.url.toString()) {
                    remitly.receiptFragment.isLoading = false
                    remitly.receiptFragment.hasErrored = true

                    webResourceError?.let {
                        val text =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) it.description.toString() else it.toString()
                        remitly.handleError(
                            RemitlyCEError(
                                ErrorMessage.connectionError,
                                IOException(text)
                            )
                        )
                    }
                    errorResponse?.let {
                        remitly.handleError(
                            RemitlyCEError(
                                ErrorMessage.serverError,
                                IOException("${it.statusCode} ${it.reasonPhrase}")
                            )
                        )
                    }

                    // TODO Retry?
                }
            }
        }
    }
}
