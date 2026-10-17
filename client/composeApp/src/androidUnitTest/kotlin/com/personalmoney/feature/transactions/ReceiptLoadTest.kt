package com.personalmoney.feature.transactions

import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.ReceiptDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLoadTest {

    private val api = mockk<HomeBudgetApi>()

    @Test
    fun `a receipt without a photo does not ask for one`() = runTest {
        val receipt = ReceiptDto(uuid = "r-1", status = "READY", hasImage = false)
        coEvery { api.receipt("r-1") } returns receipt

        val load = loadReceipt(api, "r-1")

        assertTrue(load is ReceiptLoad.Loaded)
        load as ReceiptLoad.Loaded
        assertEquals(receipt, load.receipt)
        assertNull(load.photo)
        coVerify(exactly = 0) { api.receiptImage(any()) }
    }

    @Test
    fun `a receipt with a photo asks for it`() = runTest {
        coEvery { api.receipt("r-1") } returns ReceiptDto(uuid = "r-1", status = "READY")
        coEvery { api.receiptImage("r-1") } throws IllegalStateException("not decodable here")

        val load = loadReceipt(api, "r-1")

        // The items still show when the photo will not load.
        assertTrue(load is ReceiptLoad.Loaded)
        coVerify(exactly = 1) { api.receiptImage("r-1") }
    }
}
