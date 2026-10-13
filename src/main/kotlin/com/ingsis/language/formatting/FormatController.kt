package com.ingsis.language.formatting

import com.ingsis.language.languages.LanguageRegistry
import com.ingsis.language.validation.CodeError
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * [rules] son las reglas activas con su valor (`true`, o un número para las que lo llevan), por
 * su nombre en la configuración del lenguaje. Sin reglas, el código vuelve igual.
 */
data class FormatRequest(
    val code: String,
    @field:NotBlank val language: String,
    @field:NotBlank val version: String,
    val rules: Map<String, Any?> = emptyMap(),
)

data class FormatResult(
    val content: String,
)

@RestController
class FormatController(
    private val registry: LanguageRegistry,
) {
    @PostMapping("/format")
    fun format(
        @Valid @RequestBody request: FormatRequest,
    ): FormatResult =
        FormatResult(registry.runnerFor(request.language, request.version).format(request.code, request.version, request.rules))
}

/** El código no se puede leer, así que no hay resultado que devolver. */
class InvalidCodeException(
    val errors: List<CodeError>,
) : RuntimeException("El código no es válido")
