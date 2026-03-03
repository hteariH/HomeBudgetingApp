package com.homebudget.app.controller

import com.homebudget.app.domain.CategoryType
import com.homebudget.app.dto.*
import com.homebudget.app.service.CategoryService
import com.homebudget.app.service.toResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/categories")
class CategoryController(private val service: CategoryService) {

    @GetMapping
    fun getAll(@RequestParam type: CategoryType?) = service.findAll(type).map { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long) = service.findById(id).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateCategoryRequest) = service.create(req).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: UpdateCategoryRequest) =
        service.update(id, req).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
