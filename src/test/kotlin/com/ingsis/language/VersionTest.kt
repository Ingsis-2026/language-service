package com.ingsis.language

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class VersionTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val buildProperties: BuildProperties,
) {
    @Test
    fun `responde la version de la libreria de PrintScript con la que se construyo`() {
        val version = buildProperties.get("printscript")!!
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(version), "versión inesperada: $version")

        mockMvc.get("/version").andExpect {
            status { isOk() }
            jsonPath("$.printscript") { value(version) }
        }
    }
}
