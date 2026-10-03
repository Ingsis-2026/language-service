package com.ingsis.language

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class ValidationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun validate(
        code: String,
        language: String = "printscript",
        version: String = "1.0",
    ) = mockMvc.post("/validate") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": ${quote(code)}, "language": "$language", "version": "$version"}"""
    }

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `un codigo valido no tiene errores`() {
        validate("let x: number = 5;\nprintln(x);").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
            jsonPath("$.errors.length()") { value(0) }
        }
    }

    @Test
    fun `un codigo invalido informa la regla, la linea y la columna`() {
        validate("let x: number = 5;\nprintln(x) @;").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(false) }
            jsonPath("$.errors[0].rule") { value("Carácter inválido encontrado: '@'") }
            jsonPath("$.errors[0].line") { value(2) }
            jsonPath("$.errors[0].column") { value(12) }
        }
    }

    @Test
    fun `la version cambia lo que es valido`() {
        val code = "if (true) {\n  println(1);\n}"
        validate(code, version = "1.0").andExpect { jsonPath("$.valid") { value(false) } }
        validate(code, version = "1.1").andExpect { jsonPath("$.valid") { value(true) } }
    }

    @Test
    fun `un lenguaje desconocido es un 400`() {
        validate("x", language = "cobol").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Lenguaje no soportado: cobol") }
        }
    }

    @Test
    fun `una version desconocida es un 400`() {
        validate("x", version = "9.9").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Versión no soportada de PrintScript: 9.9") }
        }
    }

    @Test
    fun `un pedido sin lenguaje es un 400`() {
        validate("x", language = "").andExpect { status { isBadRequest() } }
    }
}
