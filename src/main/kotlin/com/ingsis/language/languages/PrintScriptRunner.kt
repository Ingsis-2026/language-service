package com.ingsis.language.languages

import com.ingsis.language.execution.RunResult
import com.ingsis.language.validation.CodeError
import diagnostics.PrintScriptException
import interpreter.Interpreter
import interpreter.InterpreterException
import interpreter.Printer
import interpreter.Reader
import lexer.Lexer
import lexer.TokenMapper
import org.springframework.stereotype.Component
import parser.Parser
import version.Version

@Component
class PrintScriptRunner : LanguageRunner {
    override val language = "PrintScript"

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
            val tokens = Lexer(TokenMapper(parsed)).convertToTokens(code.lineSequence())
            Parser.forVersion(parsed).execute(tokens).count()
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
            val tokens = Lexer(TokenMapper(parsed)).convertToTokens(code.lineSequence())
            Parser.forVersion(parsed).execute(tokens).forEach { interpreter.execute(it) }
            RunResult(io.outputs, error = null)
        } catch (e: PrintScriptException) {
            RunResult(io.outputs, e.toCodeError())
        }
    }

    // Las posiciones de la librería son 0-based; al usuario se le muestran 1-based.
    private fun PrintScriptException.toCodeError(): CodeError =
        CodeError(
            rule = message ?: "Error de sintaxis",
            line = startPosition?.row?.plus(1),
            column = startPosition?.column?.plus(1),
        )
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
