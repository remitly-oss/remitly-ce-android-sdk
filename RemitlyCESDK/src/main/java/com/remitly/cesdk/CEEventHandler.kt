package com.remitly.cesdk

import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors


internal data class JsMessage(
    val topic: String?,
    val payload: JSONObject?
)

internal class CEEventHandler(val remitly: RemitlyCE) {
    companion object {
        const val CEEventType_UserActivity = "USERACTIVITY"
    }

    fun handle(message: JsMessage) {
        try {
            val eventType = message.payload?.optString("eventType")
            if (eventType.isNullOrEmpty()) {
                return
            }

            when (eventType.uppercase()) {
                CEEventType_UserActivity -> dispatchOnUserActivity()
                CEEventType.TRANSFER_SUBMITTED.toString() -> dispatchOnTransferSubmitted()
                CEEventType.ERROR.toString() -> dispatchOnError(eventType, message)
                CEEventType.LAUNCH.toString(),
                CEEventType.CLOSE.toString() -> dispatchOnNavigationEvent(eventType, message)
                else -> Log.e(RemitlyCE.TAG, "Unrecognized event type received: $eventType")
            }

        } catch (ex: Exception) {
            Log.e(RemitlyCE.TAG, "Invalid event received", ex)
        }
    }

    private fun dispatchOnUserActivity() {
        Executors.newSingleThreadExecutor().execute(Runnable {
            remitly.onUserActivity()
        })
    }

    private fun dispatchOnTransferSubmitted() {
        Executors.newSingleThreadExecutor().execute(Runnable {
            remitly.onTransferSubmitted()
        })
    }

    private fun dispatchOnError(eventType: String, message: JsMessage) {
        Executors.newSingleThreadExecutor().execute(Runnable {
            val data = message.payload?.optJSONObject("data") ?: JSONObject()
            val err = Error("User received error", Error(data.toString()))
            remitly.onError(err)
        })
    }

    private fun dispatchOnNavigationEvent(eventType: String, message: JsMessage) {
        // Not implemented
    }
}