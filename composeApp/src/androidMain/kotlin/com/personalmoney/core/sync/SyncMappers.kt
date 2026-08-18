package com.personalmoney.core.sync

import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.network.SyncAccountDto
import com.personalmoney.core.network.SyncCategoryDto
import com.personalmoney.core.network.SyncCurrencySettingDto
import com.personalmoney.core.network.SyncSubscriptionDto
import com.personalmoney.core.network.SyncTransactionDto
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Translation between Room rows and the sync wire format.
 *
 * Two things happen here and nowhere else:
 *
 * - **Money changes representation.** Locally it is a `Long` count of minor units; on
 *   the wire it is a decimal string. A JSON number would round-trip through a double,
 *   which is not a thing to do with a balance. The exponent comes from the currency, so
 *   JPY (0 digits) and BTC (8) survive the trip intact.
 * - **References change from local ids to uuids**, because the server has never heard of
 *   a row created offline.
 */
object SyncMappers {

    fun minorToDecimalString(minor: Long, decimals: Int): String =
        BigDecimal.valueOf(minor).movePointLeft(decimals).setScale(decimals).toPlainString()

    fun decimalStringToMinor(value: String, decimals: Int): Long =
        BigDecimal(value).movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).toLong()

    /** Server instants are ISO-8601; 0 locally means "never synced", which sends null. */
    fun stampToIso(stamp: Long): String? =
        if (stamp <= 0L) null else Instant.fromEpochMilliseconds(stamp).toString()

    fun isoToStamp(iso: String?): Long =
        iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

    // ── Accounts ────────────────────────────────────────────────────────────

    fun AccountEntity.toDto(decimals: Int) = SyncAccountDto(
        uuid = uuid,
        baseUpdatedAt = stampToIso(updatedAt),
        deleted = deleted,
        name = name,
        currencyCode = currencyCode,
        initialBalance = minorToDecimalString(initialBalanceMinor, decimals),
        // The server calls it description, the app calls it note - same field.
        description = note,
        icon = icon,
        color = colorToHex(color),
        archived = archived,
        sortOrder = sortOrder,
        includeInTotal = includeInTotal
    )

    fun SyncAccountDto.toEntity(existingId: Long, decimals: Int) = AccountEntity(
        id = existingId,
        uuid = uuid,
        name = name,
        currencyCode = currencyCode,
        initialBalanceMinor = decimalStringToMinor(initialBalance, decimals),
        icon = icon,
        color = hexToColor(color),
        archived = archived,
        sortOrder = sortOrder,
        includeInTotal = includeInTotal,
        note = description,
        updatedAt = isoToStamp(updatedAt),
        deleted = deleted,
        // Straight from the server, so there is nothing left to push.
        pendingSync = false
    )

    // ── Categories ──────────────────────────────────────────────────────────

    fun CategoryEntity.toDto(parentUuid: String?, baseDecimals: Int) = SyncCategoryDto(
        uuid = uuid,
        baseUpdatedAt = stampToIso(updatedAt),
        deleted = deleted,
        name = name,
        type = type.name,
        icon = icon,
        color = colorToHex(color),
        parentUuid = parentUuid,
        archived = archived,
        sortOrder = sortOrder,
        isDefault = isDefault,
        budgetLimit = budgetLimitMinor?.let { minorToDecimalString(it, baseDecimals) }
    )

    fun SyncCategoryDto.toEntity(existingId: Long, parentId: Long?, baseDecimals: Int) = CategoryEntity(
        id = existingId,
        uuid = uuid,
        name = name,
        type = CategoryType.valueOf(type),
        icon = icon,
        color = hexToColor(color),
        parentId = parentId,
        archived = archived,
        sortOrder = sortOrder,
        isDefault = isDefault,
        budgetLimitMinor = budgetLimit?.let { decimalStringToMinor(it, baseDecimals) },
        updatedAt = isoToStamp(updatedAt),
        deleted = deleted,
        pendingSync = false
    )

    // ── Transactions ────────────────────────────────────────────────────────

    fun TransactionEntity.toDto(
        accountUuid: String,
        categoryUuid: String?,
        subscriptionUuid: String?,
        counterpartUuid: String?,
        decimals: Int
    ) = SyncTransactionDto(
        uuid = uuid,
        baseUpdatedAt = stampToIso(updatedAt),
        deleted = deleted,
        accountUuid = accountUuid,
        categoryUuid = categoryUuid,
        amount = minorToDecimalString(amountMinor, decimals),
        currencyCode = currencyCode,
        accountAmount = minorToDecimalString(accountAmountMinor, decimals),
        fxRateToAccount = fxRateToAccount.toString(),
        type = type.name,
        occurredAt = occurredAt.toString(),
        description = note,
        subscriptionUuid = subscriptionUuid,
        transferGroupId = transferGroupId,
        transferCounterpartAccountUuid = counterpartUuid,
        transferFee = transferFeeMinor?.let { minorToDecimalString(it, decimals) }
    )

    fun SyncTransactionDto.toEntity(
        existingId: Long,
        accountId: Long,
        categoryId: Long?,
        subscriptionId: Long?,
        counterpartAccountId: Long?,
        decimals: Int
    ) = TransactionEntity(
        id = existingId,
        uuid = uuid,
        type = TransactionType.valueOf(type),
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = decimalStringToMinor(amount, decimals),
        currencyCode = currencyCode,
        accountAmountMinor = decimalStringToMinor(accountAmount, decimals),
        fxRateToAccount = fxRateToAccount.toDoubleOrNull() ?: 1.0,
        occurredAt = Instant.parse(occurredAt),
        createdAt = Instant.parse(occurredAt),
        note = description,
        subscriptionId = subscriptionId,
        transferGroupId = transferGroupId,
        transferCounterpartAccountId = counterpartAccountId,
        transferFeeMinor = transferFee?.let { decimalStringToMinor(it, decimals) },
        updatedAt = isoToStamp(updatedAt),
        deleted = deleted,
        pendingSync = false
    )

    // ── Subscriptions ───────────────────────────────────────────────────────

    fun SubscriptionEntity.toDto(
        accountUuid: String,
        categoryUuid: String?,
        decimals: Int
    ) = SyncSubscriptionDto(
        uuid = uuid,
        baseUpdatedAt = stampToIso(updatedAt),
        deleted = deleted,
        name = name,
        accountUuid = accountUuid,
        categoryUuid = categoryUuid,
        amount = minorToDecimalString(amountMinor, decimals),
        currencyCode = currencyCode,
        frequency = frequency.name,
        intervalCount = interval,
        nextChargeDate = nextChargeDate.toString(),
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        active = active,
        reminderMinutesBefore = reminderMinutesBefore,
        icon = icon,
        color = colorToHex(color),
        note = note
    )

    fun SyncSubscriptionDto.toEntity(
        existingId: Long,
        accountId: Long,
        categoryId: Long?,
        decimals: Int
    ) = SubscriptionEntity(
        id = existingId,
        uuid = uuid,
        name = name,
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = decimalStringToMinor(amount, decimals),
        currencyCode = currencyCode,
        frequency = SubscriptionFrequency.valueOf(frequency),
        interval = intervalCount,
        nextChargeDate = LocalDate.parse(nextChargeDate),
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let { LocalDate.parse(it) },
        active = active,
        reminderMinutesBefore = reminderMinutesBefore,
        icon = icon,
        color = hexToColor(color),
        note = note,
        updatedAt = isoToStamp(updatedAt),
        deleted = deleted,
        pendingSync = false
    )

    // ── Currency settings ───────────────────────────────────────────────────

    fun CurrencyEntity.toDto() = SyncCurrencySettingDto(
        uuid = uuid,
        baseUpdatedAt = stampToIso(updatedAt),
        deleted = deleted,
        currencyCode = code,
        active = active,
        sortOrder = sortOrder
    )

    /**
     * Colours are an ARGB Long locally and a CSS hex string on the wire, because the
     * web client renders the same value.
     */
    fun colorToHex(color: Long): String = "#%06X".format(color and 0xFFFFFF)

    fun hexToColor(hex: String): Long {
        val cleaned = hex.removePrefix("#")
        val rgb = cleaned.toLongOrNull(16) ?: 0x3D4499L
        // Restore full opacity: the wire format carries no alpha channel.
        return 0xFF000000L or (rgb and 0xFFFFFF)
    }
}
