package com.ingsis.language.linting

import com.ingsis.language.languages.LanguageRegistry
import com.ingsis.language.validation.ValidationResult
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * [rules] son las reglas activas con su valor (`true`, o el formato de identificador elegido),
 * por su nombre en la configuración del lenguaje.
 */
data class LintRequest(
    val code: String,
    @field:NotBlank val language: String,
    @field:NotBlank val version: String,
    val rules: Map<String, Any?> = emptyMap(),
)

/**
 * La respuesta tiene la misma forma que la de la validación: incumplir una regla no es un error
 * del pedido, así que es un 200 con `valid = false` y cada regla incumplida con su ubicación.
 */
@RestController
class LintController(
    private val registry: LanguageRegistry,
) {
    @PostMapping("/lint")
    fun lint(
        @Valid @RequestBody request: LintRequest,
    ): ValidationResult {
        val errors = registry.runnerFor(request.language, request.version).lint(request.code, request.version, request.rules)
        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }
}
