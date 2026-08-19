package com.personalmoney.core.util

@JsFun("(msg) => console.debug(msg)")
private external fun consoleDebug(msg: String)

@JsFun("(msg) => console.warn(msg)")
private external fun consoleWarn(msg: String)

actual object AppLog {
    actual fun d(tag: String, message: String) { consoleDebug("$tag: $message") }
    actual fun w(tag: String, message: String) { consoleWarn("$tag: $message") }
}
