package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.household.HouseholdChangedException
import com.homebudget.app.security.CustomUserDetailsService
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.token.AccessTokenService
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The MCP server: the SDK's Streamable HTTP servlet at [ENDPOINT], [McpAuthFilter] in front
 * of it, and every [McpTool] bean registered as a tool.
 */
@Configuration
class McpServerConfig {
    companion object {
        const val ENDPOINT = "/mcp"
        const val SERVER_NAME = "personalmoney"
        const val SERVER_ERROR = "Something went wrong on the server"
        const val HOUSEHOLD_CHANGED = "Your household changed — try again"
        private val log = LoggerFactory.getLogger(McpServerConfig::class.java)

        /**
         * Runs [tool] as [principal] and turns what happens into a tool result: the tool's own
         * answer, a [ToolInputException]'s message, a retry hint when the user's household
         * changed under the call, or a fixed message for anything else.
         * Logs the tool, the user and the time taken - never arguments or results.
         */
        fun invoke(tool: McpTool, principal: UserPrincipal?, args: Map<String, Any?>): CallToolResult {
            if (principal == null) return error("Not signed in") // the filter makes this unreachable
            val started = System.nanoTime()
            var failed = true
            try {
                val result = try {
                    McpPrincipalScope.runAs(principal) { tool.call(args) }
                } catch (e: ToolInputException) {
                    error(e.message ?: "Invalid input")
                } catch (e: HouseholdChangedException) {
                    // A join or leave landed after the token check; the next call sees the new household.
                    error(HOUSEHOLD_CHANGED)
                } catch (e: Exception) {
                    log.warn("mcp tool={} user={} failed: {}", tool.name, principal.getId(), e.javaClass.name)
                    error(SERVER_ERROR)
                }
                failed = result.isError == true
                return result
            } finally {
                log.info(
                    "mcp tool={} user={} ms={} error={}",
                    tool.name, principal.getId(), (System.nanoTime() - started) / 1_000_000, failed
                )
            }
        }

        private fun error(message: String) = CallToolResult.builder().addTextContent(message).isError(true).build()
    }

    @Bean
    fun mcpTransport(): HttpServletStreamableServerTransportProvider =
        HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(ENDPOINT)
            .maxRequestSize(McpAuthFilter.MAX_BODY_BYTES)
            // The principal the filter resolved travels with the message, because the SDK may
            // run the tool on another thread than the request's.
            .contextExtractor { request ->
                (request.getAttribute(McpPrincipalScope.ATTRIBUTE) as? UserPrincipal)
                    ?.let { McpTransportContext.create(mapOf(McpPrincipalScope.ATTRIBUTE to it)) }
                    ?: McpTransportContext.EMPTY
            }
            .build()

    @Bean
    fun mcpServlet(transport: HttpServletStreamableServerTransportProvider) =
        ServletRegistrationBean(transport, ENDPOINT).apply {
            setName("mcp")
            setAsyncSupported(true)
        }

    @Bean
    fun mcpAuthFilter(
        settings: McpSettings,
        tokens: AccessTokenService,
        userDetails: CustomUserDetailsService,
        rateLimiter: McpRateLimiter,
        sessions: McpSessions,
        objectMapper: ObjectMapper,
        @Value("\${app.base-url:}") baseUrl: String
    ) = FilterRegistrationBean(
        McpAuthFilter(settings, tokens, userDetails, rateLimiter, sessions, objectMapper, baseUrl)
    ).apply {
        setName("mcpAuthFilter")
        addUrlPatterns(ENDPOINT)
        setAsyncSupported(true)
    }

    @Bean(destroyMethod = "close")
    fun mcpServer(
        transport: HttpServletStreamableServerTransportProvider,
        tools: List<McpTool>,
        build: ObjectProvider<BuildProperties>
    ): McpSyncServer {
        val jsonMapper = McpJsonDefaults.getMapper()
        val specs = tools.map { tool ->
            SyncToolSpecification(
                McpSchema.Tool.builder(tool.name, jsonMapper, tool.inputSchema)
                    .description(tool.description)
                    .build()
            ) { exchange, request ->
                invoke(
                    tool,
                    exchange.transportContext().get(McpPrincipalScope.ATTRIBUTE) as? UserPrincipal,
                    request.arguments() ?: emptyMap()
                )
            }
        }
        return McpServer.sync(transport)
            .serverInfo(SERVER_NAME, build.ifAvailable?.version ?: "dev")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .tools(specs)
            .build()
    }
}
