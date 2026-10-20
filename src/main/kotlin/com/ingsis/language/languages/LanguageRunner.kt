package com.ingsis.language.languages

import com.ingsis.language.execution.RunResult
import com.ingsis.language.validation.CodeError

/**
 * Lo que el servicio sabe hacer con el código de un lenguaje.
 *
 * Hoy la única implementación es PrintScript. Un lenguaje nuevo es otra implementación como
 * bean de Spring, sin tocar los controllers: el [LanguageRegistry] la encuentra por [language].
 */
interface LanguageRunner {
    /** Nombre con el que llega el lenguaje en cada pedido. Se compara sin distinguir mayúsculas. */
    val language: String

    fun supportsVersion(version: String): Boolean

    /** Los errores que impiden que el código sea válido; vacío si lo es. */
    fun validate(
        code: String,
        version: String,
    ): List<CodeError>

    /**
     * Ejecuta el código dándole [inputs] en orden. Devuelve lo que imprimió y, si falló, el error:
     * lo impreso antes del error se conserva.
     */
    fun run(
        code: String,
        version: String,
        inputs: List<String>,
    ): RunResult

    /**
     * El código con las reglas de formato aplicadas. [rules] son las reglas activas, por su nombre
     * en la configuración del lenguaje; las que no aparecen no se tocan.
     */
    fun format(
        code: String,
        version: String,
        rules: Map<String, Any?>,
    ): String

    /** Las reglas de lint de [rules] que el código no cumple; vacío si las cumple todas. */
    fun lint(
        code: String,
        version: String,
        rules: Map<String, Any?>,
    ): List<CodeError>
}
