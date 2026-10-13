package com.ingsis.language.languages

import com.ingsis.language.execution.RunResult
import com.ingsis.language.formatting.InvalidCodeException
import com.ingsis.language.validation.CodeError
import diagnostics.PrintScriptException
import formatter.FormatterBuilderPS
import interpreter.Interpreter
import interpreter.InterpreterException
import interpreter.Printer
import interpreter.Reader
import lexer.Lexer
import lexer.TokenMapper
import linter.BrokenRule
import linter.Linter
import org.springframework.stereotype.Component
import parser.Parser
import rules.FormattingRules
import tools.jackson.databind.json.JsonMapper
import version.Version

@Component
class PrintScriptRunner : LanguageRunner {
    override val language = "PrintScript"

    private val jsonMapper = JsonMapper.builder().build()

    override fun supportsVersion(version: String): Boolean = Version.of(version) != null

    /**
     * Recorrer todos los nodos alcanza para validar: el Lexer y el Parser fallan sobre la primera
     * sentencia que no pueden resolver, así que hay a lo sumo un error.
     */
    override fun validate(
        code: String,
        version: String,
    ): List<CodeError> {
        val parsed = Version.parse(version)
        return try {
            Parser.forVersion(parsed).execute(tokensOf(code, parsed)).count()
            emptyList()
        } catch (e: PrintScriptException) {
            listOf(e.toCodeError())
        }
    }

    /** Cada sentencia se ejecuta a medida que el parser la resuelve, como en la CLI. */
    override fun run(
        code: String,
        version: String,
        inputs: List<String>,
    ): RunResult {
        val parsed = Version.parse(version)
        val io = TestIO(inputs)
        return try {
            val interpreter = Interpreter.forVersion(parsed, io, io)
            Parser.forVersion(parsed).execute(tokensOf(code, parsed)).forEach { interpreter.execute(it) }
            RunResult(io.outputs, error = null)
        } catch (e: PrintScriptException) {
            RunResult(io.outputs, e.toCodeError())
        }
    }

    /** Un código que no se puede leer no se puede formatear: es un error del pedido y no un resultado. */
    override fun format(
        code: String,
        version: String,
        rules: Map<String, Any?>,
    ): String =
        try {
            FormatterBuilderPS().build(FormattingRules.from(rules), Version.parse(version)).format(code)
        } catch (e: PrintScriptException) {
            throw InvalidCodeException(listOf(e.toCodeError()))
        }

    /**
     * Un código que no parsea no cumple ninguna regla: su error de sintaxis se informa como una
     * regla incumplida más.
     */
    override fun lint(
        code: String,
        version: String,
        rules: Map<String, Any?>,
    ): List<CodeError> {
        val parsed = Version.parse(version)
        val linter = Linter.forConfig(parsed, jsonMapper.writeValueAsString(rules.applicableTo(parsed)))
        return try {
            linter
                .check(Parser.forVersion(parsed).execute(tokensOf(code, parsed)))
                .getBrokenRules()
                .map { it.toCodeError() }
        } catch (e: PrintScriptException) {
            listOf(e.toCodeError())
        }
    }

    private fun tokensOf(
        code: String,
        version: Version,
    ) = Lexer(TokenMapper(version)).convertToTokens(code.lineSequence())

    /**
     * `readInput` no existe en 1.0, así que su regla no tiene nada que revisar ahí y la librería
     * la rechaza. Las reglas de un usuario valen para todos sus snippets, de cualquier versión:
     * en vez de fallar, se ignora.
     */
    private fun Map<String, Any?>.applicableTo(version: Version): Map<String, Any?> =
        if (version < Version.V1_1) this - READ_INPUT_RULE else this

    // Las posiciones de la librería son 0-based; al usuario se le muestran 1-based.
    private fun PrintScriptException.toCodeError(): CodeError =
        CodeError(
            rule = message ?: "Error de sintaxis",
            line = startPosition?.row?.plus(1),
            column = startPosition?.column?.plus(1),
        )

    private fun BrokenRule.toCodeError(): CodeError = CodeError(ruleDescription, errorPosition.row + 1, errorPosition.column + 1)

    private companion object {
        const val READ_INPUT_RULE = "mandatory-variable-or-literal-in-readInput"
    }
}

/**
 * Entrada y salida de una ejecución: los inputs salen de una lista y lo impreso se junta en otra.
 *
 * Los outputs son solo lo que imprime `println` (US8). La librería imprime también el mensaje de
 * `readInput` justo antes de pedir el valor, con el mismo [Printer]; por eso, al pedir un input,
 * se descarta lo último impreso si es ese mensaje.
 */
private class TestIO(
    inputs: List<String>,
) : Printer,
    Reader {
    private val pending = ArrayDeque(inputs)
    val outputs = mutableListOf<String>()

    override fun print(message: String) {
        outputs.add(message)
    }

    override fun input(message: String): String {
        if (outputs.lastOrNull() == message) outputs.removeLast()
        // Sin posición: el Interpreter la completa con la del readInput que pidió el valor.
        return pending.removeFirstOrNull() ?: throw InterpreterException("No hay más inputs para \"$message\"")
    }
}
