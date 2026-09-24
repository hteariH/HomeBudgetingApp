package com.homebudget.app.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.util.concurrent.TimeUnit

/**
 * Serves the React app: one document for the whole site, whose /app/... addresses are
 * routes of it rather than files.
 *
 * Everything under /app goes through a resource handler rather than a controller mapping on
 * purpose. The app has multi-segment routes (/app/transactions/12), and Spring's
 * PathPatternParser rejects any pattern with data after a double-wildcard element - so the
 * pattern that would mirror the top-level fallback in SpaFallbackController (a wildcard
 * followed by a no-dot capture) simply will not parse. A resolver that always answers with
 * the shell does the same job.
 *
 * (The handler patterns are not spelled out in this comment: Kotlin comments nest, and a
 * slash followed by two stars inside one opens another.)
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    private companion object {
        const val INDEX = "index.html"
        const val STATIC = "classpath:/static/"
        const val ASSETS = "classpath:/static/assets/"
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Vite gives every bundle a content-hashed name, so a file here can never change
        // meaning: cache it for as long as the browser is willing to. Deliberately no shell
        // fallback - a missing bundle must 404, not come back as HTML the browser would try
        // to run as a script.
        registry.addResourceHandler("/assets/**")
            .addResourceLocations(ASSETS)
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .resourceChain(true)

        // Every route of the app is the shell. `noCache` still allows a conditional request,
        // so an unchanged shell costs a 304 - but a changed one, naming new bundles, is never
        // missed after a deploy.
        registry.addResourceHandler("/app/**")
            .addResourceLocations(STATIC)
            .setCacheControl(CacheControl.noCache())
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? =
                    location.createRelative(INDEX).takeIf { it.exists() && it.isReadable }
            })
    }
}
