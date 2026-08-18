package com.homebudget.app.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the Compose/WebAssembly client under /app.
 *
 * This is a resource handler rather than a controller mapping on purpose. The Compose
 * client has multi-segment routes (/app/accounts/12), and Spring's PathPatternParser
 * rejects any pattern with data after a double-wildcard element - so the pattern that
 * would mirror the React fallback (a wildcard segment followed by a no-dot capture)
 * simply will not parse.
 *
 * Resolving the file first and falling back to index.html also keeps assets working
 * without the dot-matching games the React fallback needs: personalmoney.wasm exists, so
 * it is returned as itself; /app/accounts does not, so it gets the shell.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    private companion object { const val INDEX = "index.html" }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/app/**")
            .addResourceLocations("classpath:/static/app/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    // "/app/" arrives as "." (and "/app" as ""), which resolves to the
                    // directory itself and then fails with "No static resource .".
                    // Anything that names a directory rather than a file is the shell.
                    if (resourcePath.isBlank() || resourcePath == "." || resourcePath.endsWith("/")) {
                        return location.createRelative(INDEX)
                    }
                    val requested = location.createRelative(resourcePath)
                    return if (requested.exists() && requested.isReadable) requested
                    else location.createRelative(INDEX)
                }
            })
    }
}
