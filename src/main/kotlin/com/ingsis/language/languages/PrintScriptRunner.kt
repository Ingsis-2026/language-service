package com.ingsis.language.languages

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
    override fun execute(
        code: String,
        version: String,
        io: ExecutionIO,
    ): CodeError? {
        val parsed = Version.parse(version)
        val console = PrintScriptConsole(io)
        return try {
            val interpreter = Interpreter.forVersion(parsed, console, console)
            Parser.forVersion(parsed).execute(tokensOf(code, parsed)).forEach { interpreter.execute(it) }
            null
        } catch (e: PrintScriptException) {
            e.toCodeError()
        } finally {
            console.flush()
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
 * Adapta el [Printer] y el [Reader] de la librería a un [ExecutionIO].
 *
 * Los outputs son solo lo que imprime `println` (US8). La librería imprime también el mensaje de
 * `readInput` justo antes de pedir el valor, con el mismo [Printer]. Por eso cada línea se retiene
 * hasta saber qué viene después: si lo que sigue es pedir un input con ese mismo mensaje, era el
 * mensaje y no un output. Así el output sale con una sentencia de demora y no al final.
 */
private class PrintScriptConsole(
    private val io: ExecutionIO,
) : Printer,
    Reader {
    private var held: String? = null

    override fun print(message: String) {
        flush()
        held = message
    }

    override fun input(message: String): String {
        if (held == message) held = null else flush()
        // Sin posición: el Interpreter la completa con la del readInput que pidió el valor.
        return io.input(message) ?: throw InterpreterException("No hay más inputs para \"$message\"")
    }

    /** Entrega la línea retenida, si hay. Al terminar, haya error o no, también: ya no es un mensaje. */
    fun flush() {
        held?.let(io::output)
        held = null
    }
}
