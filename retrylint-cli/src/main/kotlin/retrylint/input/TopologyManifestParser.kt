package retrylint.input

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Path
import kotlin.io.path.inputStream

class TopologyManifestParser {
    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun parse(path: Path): TopologyManifest =
        path.inputStream().use(mapper::readValue)
}
