package com.ingsis.language

import com.ingsis.language.formatting.InvalidCodeException
import com.ingsis.language.languages.UnsupportedLanguageException
import com.ingsis.language.observability.withLogContext
import diagnostics.ConfigurationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Los errores del pedido van al log en DEBUG: son respuestas esperadas y quien las tiene que ver es
 * snippet-service. Salvo unas reglas que el lenguaje no acepta, que van en WARN: snippet-service solo manda
 * reglas que conoce, así que los dos servicios no están de acuerdo en cuáles son.
 */
@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(UnsupportedLanguageException::class)
    fun unsupportedLanguage(e: UnsupportedLanguageException): ProblemDetail {
        log.debug("Lenguaje o versión no soportados: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
    }

    /** Las reglas que llegaron no son una configuración que el lenguaje acepte. */
    @ExceptionHandler(ConfigurationException::class)
    fun invalidRules(e: ConfigurationException): ProblemDetail {
        log.warn("Las reglas no son una configuración válida: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
    }

    @ExceptionHandler(InvalidCodeException::class)
    fun invalidCode(e: InvalidCodeException): ProblemDetail {
        withLogContext("errors" to e.errors.size) { log.debug("No se puede formatear un código inválido") }
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.message).apply {
            setProperty("errors", e.errors)
        }
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ErrorHandler::class.java)
    }
}
