package com.personalmoney.core.homemoney

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * JSON shapes returned by [HomeMoneyApi]. Field names mirror the wire keys via [SerialName].
 * Every field is optional with a default so a partial or evolving response never throws — the
 * shared [kotlinx.serialization.json.Json] is configured with `ignoreUnknownKeys` + `coerceInputValues`.
 *
 * Observed wire samples (HTTP 200 in both error and success cases):
 *  - TokenPassword (bad login): {"Error":{"code":2,"message":"Invalid username"},"access_token":null,...}
 *  - BalanceList (bad token):   {"Error":{"code":7,"message":"Invalid token"},"defaultcurrency":null,"name":null,"ListGroupInfo":[]}
 */

@Serializable
data class ApiError(
    val code: Int = 0,
    val message: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("Error") val error: ApiError? = null,
    @SerialName("access_token") val accessToken: String? = null,
)

@Serializable
data class BalanceListResponse(
    @SerialName("Error") val error: ApiError? = null,
    @SerialName("defaultcurrency") val defaultCurrency: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("ListGroupInfo") val groups: List<GroupInfo> = emptyList(),
)

@Serializable
data class GroupInfo(
    val id: String? = null,
    val name: String? = null,
    @SerialName("ListAccountInfo") val accounts: List<AccountInfo> = emptyList(),
)

@Serializable
data class AccountInfo(
    val id: String? = null,
    val name: String? = null,
    /** Whether HomeMoney shows the account (hidden accounts have display=false). */
    val display: Boolean = true,
    /** Whether the account counts toward the user's total balance in HomeMoney. */
    val includeInBalance: Boolean = true,
    @SerialName("ListCurrencyInfo") val currencies: List<CurrencyInfo> = emptyList(),
)

@Serializable
data class CurrencyInfo(
    val id: String? = null,
    /** ISO-ish currency code, e.g. "UAH", "USD". */
    val shortname: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val balance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal = BigDecimal.ZERO,
    val display: Boolean = true,
)

/**
 * Reads a monetary value whether the server emits it as a JSON number (`123.45`) or a quoted
 * string (`"123.45"`). ASMX decimal serialization has historically used both; this keeps balances
 * exact (no float round-trip) and never throws — an unparseable value degrades to zero.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val raw = if (decoder is JsonDecoder) {
            (decoder.decodeJsonElement() as? JsonPrimitive)?.content
        } else {
            decoder.decodeString()
        }
        return raw?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }
}
