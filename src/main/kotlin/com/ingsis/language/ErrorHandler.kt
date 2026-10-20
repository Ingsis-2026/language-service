package com.ingsis.language

import com.ingsis.language.formatting.InvalidCodeException
import com.ingsis.language.languages.UnsupportedLanguageException
import diagnostics.ConfigurationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(UnsupportedLanguageException::class)
    fun unsupportedLanguage(e: UnsupportedLanguageException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)

    /** Las reglas que llegaron no son una configuración que el lenguaje acepte. */
    @ExceptionHandler(ConfigurationException::class)
    fun invalidRules(e: ConfigurationException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(InvalidCodeException::class)
    fun invalidCode(e: InvalidCodeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.message).apply {
            setProperty("errors", e.errors)
        }
}
