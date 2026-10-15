package com.homebudget.app.receipt

import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/receipts")
class ReceiptController(private val service: ReceiptService) {

    /** Accepts a photo (JPEG, PNG or WebP, up to 15 MB) for reading; poll the uuid it returns. */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestPart("image") image: MultipartFile): ResponseEntity<ReceiptAccepted> {
        val receipt = service.upload(image)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ReceiptAccepted(receipt.uuid, receipt.status))
    }

    @GetMapping("/{uuid}")
    fun get(@PathVariable uuid: String): ReceiptResponse = service.get(uuid)

    // No `produces`: an error here is answered as JSON, which a produces=image/jpeg would refuse.
    @GetMapping("/{uuid}/image")
    fun image(@PathVariable uuid: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore())
            .body(service.image(uuid))
}
