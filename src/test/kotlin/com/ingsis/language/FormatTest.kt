package com.ingsis.language

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** US11: formatear código con las reglas que eligió el usuario. */
@SpringBootTest
@AutoConfigureMockMvc
class FormatTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun format(
        code: String,
        rules: String = "{}",
        language: String = "printscript",
        version: String = "1.0",
    ) = mockMvc.post("/format") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": ${quote(code)}, "language": "$language", "version": "$version", "rules": $rules}"""
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `aplica las reglas activas`() {
        val rules =
            """{"enforce-spacing-before-colon-in-declaration": true, "enforce-spacing-after-colon-in-declaration": true,
                "enforce-spacing-around-equals": true}"""
        format("let x:number=5;", rules).andExpect {
            status { isOk() }
            jsonPath("$.content") { value("let x : number = 5;") }
        }
    }

    @Test
    fun `sin reglas el codigo vuelve igual`() {
        format("let x:number=5;").andExpect {
            status { isOk() }
            jsonPath("$.content") { value("let x:number=5;") }
        }
    }

    @Test
    fun `las reglas con valor reciben un numero`() {
        val code = "if (true) {\nprintln(1);\n}"
        format(code, """{"indent-inside-if": 2}""", version = "1.1").andExpect {
            status { isOk() }
            jsonPath("$.content") { value("if (true) {\n  println(1);\n}") }
        }
    }

    @Test
    fun `un codigo que no se puede leer es un 422 con la regla, la linea y la columna`() {
        format("let x: number = 5;\nlet y: number = @;").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].rule") { exists() }
            jsonPath("$.errors[0].line") { value(2) }
        }
    }

    @Test
    fun `un lenguaje desconocido es un 400`() {
        format("x", language = "cobol").andExpect { status { isBadRequest() } }
    }
}
