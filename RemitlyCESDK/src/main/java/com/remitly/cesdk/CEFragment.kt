package com.remitly.cesdk

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.remitly.cesdk.RemitlyCE.Companion.TAG
import org.json.JSONObject

internal class CEFragment : DialogFragment() {

    private lateinit var myView: View
    private var isRecreatingView = false
    private var isLargeLayout = false

    enum class CloseRequestKind(string: String) {
        web("web_close_button_pressed"),
        native("modal_close_button_pressed"),
        system("system_close_button_pressed")
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
            isCloseButtonVisible = value
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        myView = inflater.inflate(R.layout.ce_fragment, container, false)
        isLargeLayout = resources.getBoolean(R.bool.large_layout)

        context.remitly.webView.setupWebView(context)
        setupCloseButton()
        setupErrorButton()

        return myView
    }

    private val keyEventListener = DialogInterface.OnKeyListener(fun(
        _: DialogInterface,
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK
            && event.action == KeyEvent.ACTION_DOWN
            && context.remitly.webView.canGoBack()
        ) {
            context.remitly.webView.goBack()
            return true
        }
        return false
    })

    // Overriding onCreateDialog and onStart so the fragment can be full-screen,
    // not showing the action bar.
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // If we have saved instance state that means we're recreating the fragment
        // after a lifecycle event.
        if (savedInstanceState != null) {
            isRecreatingView = true
        }

        dialog.setOnKeyListener(keyEventListener)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    internal fun dismiss(closeRequestKind: CloseRequestKind) {
        handleClose(closeRequestKind)
        super.dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        context.remitly.onDismissed()
    }

    override fun onResume() {
        super.onResume()

        // We can't handle recreating the fragment once lifecycle loses the config data,
        // so we close the modal.
        if (isRecreatingView) {
            dismiss(CloseRequestKind.system)
        }
    }

    internal fun attachWebView(webView: WebView) {
        myView.findViewById<FrameLayout>(R.id.ce_fragment)?.addView(webView, 0)
    }

    private fun setupCloseButton() {
        val closeButton: View? = myView.findViewById<ImageButton>(R.id.ce_receipt_close)
        closeButton?.setOnClickListener { dismiss(CloseRequestKind.native) }
    }

    private fun setupErrorButton() {
        val errorButton: View? = myView.findViewById<ImageButton>(R.id.ce_error_button)
        errorButton?.setOnClickListener {
            context.remitly.webView.loadUrlInBrowser(
                Uri.parse(
                    REMITLY_HELP_CENTER_URL
                )
            )
        }
    }

    internal fun handlePresent() {
        context.remitly.hostActivity?.let {
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
            lastUrl = context.remitly.webView.currentUrl()
            context.remitly.webView.loadUrl("about:blank")
            context.remitly.webView.destroyWebView()
        } catch (_: Exception) {
            // ignore
        }

        val payload = JSONObject().put("eventType", "close")
        context.remitly.eventHandler.handle(JsMessage("event", payload))
        context.remitly.eventLogger.logEvent(
            "closeWebView",
            mapOf(
                "url" to lastUrl,
                "closeReason" to closeRequestKind
            )
        )
    }
}