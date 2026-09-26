package com.homebudget.app.controller

import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.Tag
import com.homebudget.app.domain.TransactionTag
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.repository.TagRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.TransactionTagRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant

data class TagResponse(
    val id: Long,
    val uuid: String,
    val name: String,
    val color: String
)

data class CreateTagRequest(
    val name: String,
    val color: String = "#6366F1"
)

data class UpdateTagRequest(
    val name: String? = null,
    val color: String? = null
)

data class TagReportItem(
    val transactionId: Long,
    val transactionUuid: String,
    val description: String?,
    val occurredAt: Instant,
    val amount: BigDecimal,
    val currencyCode: String,
    val type: TransactionType,
    val categoryName: String?,
    val accountName: String
)

data class TagReportResponse(
    val tagId: Long,
    val tagName: String,
    val color: String,
    val totalTransactions: Int,
    val totalExpense: BigDecimal,
    val totalIncome: BigDecimal,
    val transactions: List<TagReportItem>
)

data class AutoTagRequest(
    val description: String,
    val transactionId: Long? = null
)

data class AutoTagResponse(
    val detectedTags: List<String>,
    val boundTags: List<TagResponse>
)

@RestController
@RequestMapping("/api/tags")
class TagController(
    private val tagRepo: TagRepository,
    private val transactionTagRepo: TransactionTagRepository,
    private val transactionRepo: TransactionRepository,
    private val securityUtils: SecurityUtils
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): List<TagResponse> {
        val user = securityUtils.getCurrentUser()
        return tagRepo.findAllByUser(user).map { TagResponse(it.id, it.uuid, it.name, it.color) }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun get(@PathVariable id: Long): TagResponse {
        val user = securityUtils.getCurrentUser()
        val tag = tagRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found")
        return TagResponse(tag.id, tag.uuid, tag.name, tag.color)
    }

    @PostMapping
    @Transactional
    fun create(@RequestBody req: CreateTagRequest): TagResponse {
        val user = securityUtils.getCurrentUser()
        val cleanName = req.name.trim().removePrefix("#")
        val existing = tagRepo.findByUserAndNameIgnoreCase(user, cleanName)
        if (existing != null) {
            return TagResponse(existing.id, existing.uuid, existing.name, existing.color)
        }
        val tag = tagRepo.save(
            Tag(
                user = user,
                name = cleanName,
                color = req.color
            )
        )
        return TagResponse(tag.id, tag.uuid, tag.name, tag.color)
    }

    @PutMapping("/{id}")
    @Transactional
    fun update(@PathVariable id: Long, @RequestBody req: UpdateTagRequest): TagResponse {
        val user = securityUtils.getCurrentUser()
        val tag = tagRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found")
        req.name?.let { tag.name = it.trim().removePrefix("#") }
        req.color?.let { tag.color = it }
        tag.updatedAt = Instant.now()
        val saved = tagRepo.save(tag)
        return TagResponse(saved.id, saved.uuid, saved.name, saved.color)
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long) {
        val user = securityUtils.getCurrentUser()
        val tag = tagRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found")
        tag.deleted = true
        tag.updatedAt = Instant.now()
        tagRepo.save(tag)
    }

    /**
     * Auto-detects `#hashtags` from text (e.g. note or description) and binds them.
     */
    @PostMapping("/auto-tag")
    @Transactional
    fun autoTag(@RequestBody req: AutoTagRequest): AutoTagResponse {
        val user = securityUtils.getCurrentUser()
        val regex = Regex("""#([A-Za-z0-9_\-]+)""")
        val matches = regex.findAll(req.description).map { it.groupValues[1] }.distinct().toList()

        val boundTags = matches.map { rawName ->
            val cleanName = rawName.trim().lowercase()
            val tag = tagRepo.findByUserAndNameIgnoreCase(user, cleanName)
                ?: tagRepo.save(Tag(user = user, name = cleanName))

            if (req.transactionId != null) {
                val tx = transactionRepo.findByIdAndUser(req.transactionId, user)
                if (tx != null && transactionTagRepo.findByTransactionIdAndTagId(tx.id, tag.id) == null) {
                    transactionTagRepo.save(
                        TransactionTag(
                            user = user,
                            transaction = tx,
                            tag = tag
                        )
                    )
                }
            }
            TagResponse(tag.id, tag.uuid, tag.name, tag.color)
        }

        return AutoTagResponse(detectedTags = matches, boundTags = boundTags)
    }

    /**
     * Aggregates transactions tagged with this tag across all accounts and categories.
     */
    @GetMapping("/{id}/report")
    @Transactional(readOnly = true)
    fun getReport(@PathVariable id: Long): TagReportResponse {
        val user = securityUtils.getCurrentUser()
        val tag = tagRepo.findByIdAndUser(id, user)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found")

        val txTags = transactionTagRepo.findByTagId(tag.id)
        val items = mutableListOf<TagReportItem>()
        var totalExpense = BigDecimal.ZERO
        var totalIncome = BigDecimal.ZERO

        for (tt in txTags) {
            val tx = tt.transaction
            val decimals = tx.account.currency.decimals
            val amount = MinorUnits.toDecimal(tx.amountMinor, decimals)
            when (tx.type) {
                TransactionType.EXPENSE -> totalExpense += amount
                TransactionType.INCOME -> totalIncome += amount
                TransactionType.TRANSFER -> {}
            }
            items += TagReportItem(
                transactionId = tx.id,
                transactionUuid = tx.uuid,
                description = tx.description,
                occurredAt = tx.occurredAt,
                amount = amount,
                currencyCode = tx.currencyCode,
                type = tx.type,
                categoryName = tx.category?.name,
                accountName = tx.account.name
            )
        }

        return TagReportResponse(
            tagId = tag.id,
            tagName = tag.name,
            color = tag.color,
            totalTransactions = items.size,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            transactions = items
        )
    }
}
