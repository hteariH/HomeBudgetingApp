package com.personalmoney.core.util

/**
 * The little logging shared code needs. Android keeps going through Logcat; the browser
 * uses the console. Deliberately tiny - this is not a logging framework.
 */
expect object AppLog {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
}
