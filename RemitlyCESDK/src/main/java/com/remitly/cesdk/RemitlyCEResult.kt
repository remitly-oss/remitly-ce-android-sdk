package com.remitly.cesdk

enum class ErrorMessage {
    notInitialized,
    invalidConfiguration,
    couldntCreateWebView,
    connectionError,
    serverError,
    logoutError,
    dismissError,
}
class RemitlyCEError(message: ErrorMessage, cause: Throwable? = null) : Exception(message.name, cause)


