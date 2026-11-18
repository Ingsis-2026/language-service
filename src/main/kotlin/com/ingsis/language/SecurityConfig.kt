package com.ingsis.language

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Todos los endpoints piden un access token de Auth0, salvo el health check. Solo llama a este
 * servicio snippet-service, con un token machine-to-machine (client credentials) para la API
 * interna. El WebSocket de `/execution` también: el token va en el header `Authorization` del
 * pedido HTTP que abre la conexión, que pasa por estos mismos filtros.
 *
 * Con `auth.enabled` en false no pide nada: es para el docker compose local, sin la aplicación
 * machine-to-machine de Auth0.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Value("\${auth.enabled:true}") enabled: Boolean,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                if (enabled) authorize(anyRequest, authenticated) else authorize(anyRequest, permitAll)
            }
            if (enabled) oauth2ResourceServer { jwt { } }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
        }
        return http.build()
    }
}
