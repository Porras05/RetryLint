package retrylint.input

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Path
import java.nio.file.Files
import java.io.IOException
import kotlin.io.path.inputStream

class TopologyManifestParser {
    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun parse(path: Path): TopologyManifest {
        if (!Files.isRegularFile(path)) throw IOException("manifest is not a regular file: '$path'")
        val size = Files.size(path)
        if (size > InputLimits.MAX_MANIFEST_BYTES) {
            throw IOException("manifest exceeds ${InputLimits.MAX_MANIFEST_BYTES} byte limit: '$path'")
        }
        return path.inputStream().use(mapper::readValue)
    }
}
