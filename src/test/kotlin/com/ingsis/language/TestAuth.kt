package com.ingsis.language

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.request.RequestPostProcessor

/**
 * Un pedido de snippet-service: un access token machine-to-machine, como los que emite Auth0 con
 * client credentials. No se valida contra Auth0, así que los tests no necesitan red.
 */
fun asSnippetService(): RequestPostProcessor = jwt().jwt { it.subject("snippet-service@clients") }

/**
 * Para los tests con un servidor de verdad, como el WebSocket, donde no hay MockMvc: acepta solo
 * [TOKEN] y rechaza cualquier otro, sin hablar con Auth0.
 */
@TestConfiguration
class TestJwtDecoder {
    @Bean
    fun jwtDecoder() =
        JwtDecoder { token ->
            if (token != TOKEN) throw BadJwtException("Token inválido")
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .subject("snippet-service@clients")
                .build()
        }

    companion object {
        const val TOKEN = "token-de-snippet-service"
    }
}
