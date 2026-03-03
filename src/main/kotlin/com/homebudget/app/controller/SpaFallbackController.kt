package com.homebudget.app.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// Serves the React SPA index.html for all client-side routes.
// Spring Boot's default resource handler serves the real static assets (.js, .css, etc.).
@Controller
class SpaFallbackController {

    @GetMapping("/")
    fun root(): String = "forward:/index.html"

    @GetMapping("/{path:[^\\.]*}")
    fun spa(): String = "forward:/index.html"
}
