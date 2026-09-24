package com.homebudget.app.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// Serves the React SPA index.html for all client-side routes.
// Spring Boot's default resource handler serves the real static assets (.js, .css, etc.).
@Controller
class SpaFallbackController {

    @GetMapping("/")
    fun root(): String = "forward:/index.html"

    /**
     * Deeper /app routes reach the shell through a resource handler (see WebConfig), but the
     * bare address has to be handled here: Spring resolves "/app/" to a resource path of ".",
     * rejects that as an invalid path before any resolver runs, and answers 500.
     */
    @GetMapping("/app", "/app/")
    fun appRoot(): String = "forward:/index.html"

    @GetMapping("/{path:[^\\.]*}")
    fun spa(): String = "forward:/index.html"
}
