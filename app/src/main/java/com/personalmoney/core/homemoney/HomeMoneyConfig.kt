package com.personalmoney.core.homemoney

/**
 * OAuth application credentials for the HomeMoney (homemoney.ua) API.
 *
 * These are *app-level* credentials issued when the app is registered at homemoney.ua — they are
 * not the end user's login. The user supplies their own HomeMoney username/password at import time,
 * which is exchanged for an access token via the password grant ([HomeMoneyApi.tokenPassword]).
 *
 * NOTE: because the app ships as an APK, the secret here is not truly confidential. That is an
 * accepted limitation of the OAuth password grant in a distributed client. Keep these in one place
 * so they are trivial to rotate.
 */
object HomeMoneyConfig {
    const val CLIENT_ID = "111"
    const val CLIENT_SECRET = "6ff997066dbd36297b34b4f06640abd291a15512993fb9e197dca3cf7e281508"

    /** ASMX endpoint root. The HTTP POST form variant returns JSON (ScriptService enabled). */
    const val BASE_URL = "https://homemoney.ua/api/api2.asmx/"
}
