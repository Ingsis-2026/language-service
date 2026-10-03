package com.ingsis.language

import com.ingsis.language.languages.UnsupportedLanguageException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(UnsupportedLanguageException::class)
    fun unsupportedLanguage(e: UnsupportedLanguageException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
}
