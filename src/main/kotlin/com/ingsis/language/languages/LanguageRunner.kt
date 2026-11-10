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
     * Ejecuta el código hablando con [io] a medida que corre: cada output sale apenas se imprime y
     * cada input se pide recién cuando el código lo necesita (US9 y US10). Devuelve el error que
     * cortó la ejecución, o null si terminó bien.
     */
    fun execute(
        code: String,
        version: String,
        io: ExecutionIO,
    ): CodeError?

    /**
     * Ejecuta el código dándole [inputs] en orden. Devuelve lo que imprimió y, si falló, el error:
     * lo impreso antes del error se conserva.
     */
    fun run(
        code: String,
        version: String,
        inputs: List<String>,
    ): RunResult {
        val io = ListIO(inputs)
        val error = execute(code, version, io)
        return RunResult(io.outputs, error)
    }

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

/**
 * Por donde una ejecución saca lo que imprime y pide lo que lee. Lo implementa quien la corre: una
 * lista para `POST /run` o el WebSocket de `/execution`.
 */
interface ExecutionIO {
    /** Algo que el código imprimió, como `println`. */
    fun output(value: String)

    /**
     * El valor para un input que el código pide con [message]. Null si no hay más: el lenguaje lo
     * informa como un error de ejecución en la línea que lo pidió.
     */
    fun input(message: String): String?
}

/**
 * Corta una ejecución desde afuera del código, por ejemplo porque nadie mandó el input que pidió.
 * No es un error del lenguaje: lo atrapa quien la corre y no el intérprete.
 */
class ExecutionAbortedException(
    message: String,
) : RuntimeException(message)

/** Los inputs salen de una lista y lo impreso se junta en otra. */
private class ListIO(
    inputs: List<String>,
) : ExecutionIO {
    private val pending = ArrayDeque(inputs)
    val outputs = mutableListOf<String>()

    override fun output(value: String) {
        outputs.add(value)
    }

    override fun input(message: String): String? = pending.removeFirstOrNull()
}
