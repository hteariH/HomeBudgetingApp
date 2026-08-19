package com.homebudget.app.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.util.concurrent.TimeUnit

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
 *
 * The files arrive from the AndroidPersonalMoney repository as a release asset; see the
 * Dockerfile and `web.bundle.tag` in pom.xml.
 */
@Configuration
class WebConfig : WebMvcConfigurer {

    private companion object {
        const val INDEX = "index.html"
        const val LOCATION = "classpath:/static/app/"
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Registered first, so it wins over the catch-all below.
        //
        // The .wasm files are the only ones the build gives content-hashed names, and they
        // are also the expensive ones - seven megabytes that would otherwise be revalidated
        // on every page load. A hashed name cannot change meaning, so this is safe to cache
        // for as long as the browser is willing to.
        //
        // Deliberately no index.html fallback here: a missing .wasm must 404 rather than
        // return HTML, which the browser would try to compile and fail on with an error
        // that says nothing about the real problem.
        registry.addResourceHandler("/app/*.wasm")
            .addResourceLocations(LOCATION)
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .resourceChain(true)

        // Everything else: the shell and personalmoney.js, both of which keep fixed names
        // and change with every deploy. `noCache` still allows a conditional request, so
        // an unchanged file costs a 304 rather than a re-download - but a changed one is
        // never missed, which is what matters when index.html names the bundle.
        registry.addResourceHandler("/app/**")
            .addResourceLocations(LOCATION)
            .setCacheControl(CacheControl.noCache())
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
