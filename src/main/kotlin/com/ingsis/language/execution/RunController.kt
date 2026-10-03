package com.ingsis.language.execution

import com.ingsis.language.languages.LanguageRegistry
import com.ingsis.language.validation.CodeError
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class RunRequest(
    val code: String,
    @field:NotBlank val language: String,
    @field:NotBlank val version: String,
    val inputs: List<String> = emptyList(),
)

/** Lo que imprimió el código, en orden, y el error que cortó la ejecución si lo hubo. */
data class RunResult(
    val outputs: List<String>,
    val error: CodeError?,
)

/**
 * Igual que en la validación, un código que falla al ejecutarse no es un error del pedido: la
 * respuesta es 200 con el error adentro.
 */
@RestController
class RunController(
    private val registry: LanguageRegistry,
) {
    @PostMapping("/run")
    fun run(
        @Valid @RequestBody request: RunRequest,
    ): RunResult = registry.runnerFor(request.language, request.version).run(request.code, request.version, request.inputs)
}
