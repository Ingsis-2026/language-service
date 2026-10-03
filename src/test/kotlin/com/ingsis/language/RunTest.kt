package com.ingsis.language

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class RunTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun run(
        code: String,
        inputs: List<String> = emptyList(),
        language: String = "printscript",
        version: String = "1.1",
    ) = mockMvc.post("/run") {
        contentType = MediaType.APPLICATION_JSON
        content =
            """{"code": ${quote(code)}, "language": "$language", "version": "$version",
               "inputs": [${inputs.joinToString { quote(it) }}]}"""
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `devuelve lo que imprime cada println, en orden`() {
        run("let x: number = 5;\nprintln(x);\nprintln(\"hola\");", version = "1.0").andExpect {
            status { isOk() }
            jsonPath("$.outputs") { value(contains("5", "hola")) }
            jsonPath("$.error") { value(null) }
        }
    }

    @Test
    fun `los inputs se consumen en orden y el mensaje de readInput no es un output`() {
        val code =
            """
            let nombre: string = readInput("Nombre?");
            let apellido: string = readInput("Apellido?");
            println("Hola " + nombre + " " + apellido);
            """.trimIndent()
        run(code, inputs = listOf("Ana", "Lopez")).andExpect {
            status { isOk() }
            jsonPath("$.outputs") { value(contains("Hola Ana Lopez")) }
            jsonPath("$.error") { value(null) }
        }
    }

    @Test
    fun `si faltan inputs la ejecucion falla en el readInput que lo pidio`() {
        run("println(\"antes\");\nlet n: string = readInput(\"Nombre?\");").andExpect {
            status { isOk() }
            jsonPath("$.outputs") { value(contains("antes")) }
            jsonPath("$.error.rule") { value("No hay más inputs para \"Nombre?\"") }
            jsonPath("$.error.line") { value(2) }
        }
    }

    @Test
    fun `un error de ejecucion conserva lo impreso antes`() {
        run("println(1);\nprintln(noExiste);").andExpect {
            status { isOk() }
            jsonPath("$.outputs") { value(contains("1")) }
            jsonPath("$.error.rule") { exists() }
            jsonPath("$.error.line") { value(2) }
        }
    }

    @Test
    fun `un codigo que no imprime nada no tiene outputs`() {
        run("let x: number = 1;").andExpect {
            jsonPath("$.outputs") { value(empty<Any>()) }
        }
    }

    @Test
    fun `un lenguaje desconocido es un 400`() {
        run("x", language = "cobol").andExpect { status { isBadRequest() } }
    }
}
