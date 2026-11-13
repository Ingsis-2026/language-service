package com.ingsis.language.version

import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** La versión de la librería de PrintScript con la que este servicio valida, lintea, formatea y ejecuta. */
data class VersionResponse(
    val printscript: String,
)

/**
 * Con cada versión nueva del parser hay que volver a validar todos los snippets. La versión sale
 * de `printscriptVersion` en build.gradle.kts, así que cambia exactamente cuando se despliega este
 * servicio con otra librería; snippet-service la consulta para darse cuenta.
 */
@RestController
class VersionController(
    buildProperties: BuildProperties,
) {
    private val version =
        VersionResponse(requireNotNull(buildProperties.get("printscript")) { "Falta la versión de PrintScript en build-info.properties" })

    @GetMapping("/version")
    fun version(): VersionResponse = version
}
