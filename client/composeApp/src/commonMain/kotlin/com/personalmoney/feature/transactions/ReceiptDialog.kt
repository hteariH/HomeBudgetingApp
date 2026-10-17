package com.personalmoney.feature.transactions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.ReceiptDto
import com.personalmoney.resources.Res
import com.personalmoney.resources.action_close
import com.personalmoney.resources.receipt_items
import com.personalmoney.resources.receipt_title
import com.personalmoney.resources.receipt_unavailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal sealed interface ReceiptLoad {
    data object Loading : ReceiptLoad
    data class Loaded(val receipt: ReceiptDto, val photo: ImageBitmap?) : ReceiptLoad
    data object Unavailable : ReceiptLoad
}

/**
 * The photo and the items of the receipt an entry was scanned from.
 *
 * Online only: the phone keeps the receipt's uuid and nothing else, so both come from the
 * server each time this opens. That is deliberate - a photo per entry is not something to
 * keep on a phone that may be shared or lost.
 */
@Composable
fun ReceiptDialog(
    receiptUuid: String,
    onDismiss: () -> Unit,
    api: HomeBudgetApi = koinInject(),
) {
    val load by produceState<ReceiptLoad>(ReceiptLoad.Loading, receiptUuid) {
        value = loadReceipt(api, receiptUuid)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.receipt_title)) },
        text = {
            when (val state = load) {
                ReceiptLoad.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                ReceiptLoad.Unavailable -> Text(stringResource(Res.string.receipt_unavailable))
                is ReceiptLoad.Loaded -> ReceiptContent(state)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}

@Composable
private fun ReceiptContent(state: ReceiptLoad.Loaded) {
    val receipt = state.receipt
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.photo?.let { photo ->
            Image(
                bitmap = photo,
                contentDescription = receipt.merchant,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            )
        }
        if (receipt.items.isNotEmpty()) {
            Text(
                stringResource(Res.string.receipt_items),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            val currency = receipt.currency?.let { " $it" }.orEmpty()
            receipt.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = item.quantity?.let { "${item.name} × $it" } ?: item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(item.price + currency, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
internal suspend fun loadReceipt(api: HomeBudgetApi, uuid: String): ReceiptLoad =
    try {
        val receipt = api.receipt(uuid)
        // The items are worth showing even if the photo is gone or will not decode.
        // An entry an assistant recorded has no photo; asking would only earn a 404.
        val photo = if (!receipt.hasImage) null else try {
            val bytes = api.receiptImage(uuid)
            withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        ReceiptLoad.Loaded(receipt, photo)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Offline, most often - the receipt itself lives on the server.
        ReceiptLoad.Unavailable
    }
