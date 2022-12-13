package com.remitly.cesdk

import org.json.JSONObject

data class RemitlyCEEvent(
    /**
     * Event type for the navigation event.
     */
    val eventType: CEEventType,

    /**
     * Payload for the event.
     */
    val data: JSONObject
)

enum class CEEventType {
    /**
     * When the remittance experience is launched, the "LAUNCH" Navigation event will fire.
     */
    LAUNCH,

    /**
     * When the remittance experience is closed by a user, the "CLOSE" Navigation event will fire.
     */
    CLOSE,

    /**
     * When the remittance experience experiences an error, the "ERROR" Navigation event will fire.
     */
    ERROR,

    /**
     * When a transfer has been successfully started by a user, the "TRANSFER_SUBMITTED" Navigation event will fire.
     */
    TRANSFER_SUBMITTED;
}
