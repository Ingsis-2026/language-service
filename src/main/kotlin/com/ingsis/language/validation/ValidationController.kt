package com.ingsis.language.validation

import com.ingsis.language.languages.LanguageRegistry
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ValidationRequest(
    val code: String,
    @field:NotBlank val language: String,
    @field:NotBlank val version: String,
)

/** Una regla del lenguaje que el código no cumple. La ubicación es 1-based y puede faltar. */
data class CodeError(
    val rule: String,
    val line: Int?,
    val column: Int?,
)

data class ValidationResult(
    val valid: Boolean,
    val errors: List<CodeError>,
)

/**
 * Un código inválido no es un error del pedido: la respuesta es 200 con `valid = false`.
 * Solo un lenguaje o versión que no existen es un 400.
 */
@RestController
class ValidationController(
    private val registry: LanguageRegistry,
) {
    @PostMapping("/validate")
    fun validate(
        @Valid @RequestBody request: ValidationRequest,
    ): ValidationResult {
        val errors = registry.runnerFor(request.language, request.version).validate(request.code, request.version)
        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }
}
