package com.ingsis.language

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** US14: lintear código con las reglas que eligió el usuario. */
@SpringBootTest
@AutoConfigureMockMvc
class LintTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun lint(
        code: String,
        rules: String = "{}",
        language: String = "printscript",
        version: String = "1.1",
    ) = mockMvc.post("/lint") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": ${quote(code)}, "language": "$language", "version": "$version", "rules": $rules}"""
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `sin reglas activas todo codigo valido las cumple`() {
        lint("let my_var: number = 1;\nprintln(my_var + 1);").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
            jsonPath("$.errors.length()") { value(0) }
        }
    }

    @Test
    fun `informa cada regla incumplida con su linea y columna`() {
        lint("let x: number = 1;\nlet my_var: number = 2;", """{"identifier_format": "camel case"}""").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(false) }
            jsonPath("$.errors.length()") { value(1) }
            jsonPath("$.errors[0].rule") { value("The following identifier must be in camel case") }
            jsonPath("$.errors[0].line") { value(2) }
            jsonPath("$.errors[0].column") { value(5) }
        }
    }

    @Test
    fun `un codigo que cumple las reglas activas es valido`() {
        lint("let myVar: number = 1;\nprintln(myVar);", """{"identifier_format": "camel case"}""").andExpect {
            jsonPath("$.valid") { value(true) }
        }
    }

    @Test
    fun `println con una expresion incumple su regla`() {
        lint("println(1 + 2);", """{"mandatory-variable-or-literal-in-println": true}""").andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.errors[0].rule") { value("Println must not be called with an expression") }
        }
    }

    @Test
    fun `readInput con una expresion incumple su regla en 1_1`() {
        lint("let n: string = readInput(\"a\" + \"b\");", """{"mandatory-variable-or-literal-in-readInput": true}""").andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.errors[0].rule") { value("ReadInputs must not be called with an expression") }
        }
    }

    @Test
    fun `la regla de readInput se ignora en 1_0, donde readInput no existe`() {
        lint("let x: number = 1;", """{"mandatory-variable-or-literal-in-readInput": true}""", version = "1.0").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
        }
    }

    @Test
    fun `un codigo que no parsea no cumple las reglas e informa su error`() {
        lint("let x: number = 1;\nprintln(x) @;").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(false) }
            jsonPath("$.errors[0].line") { value(2) }
        }
    }

    @Test
    fun `un lenguaje desconocido es un 400`() {
        lint("x", language = "cobol").andExpect { status { isBadRequest() } }
    }
}
