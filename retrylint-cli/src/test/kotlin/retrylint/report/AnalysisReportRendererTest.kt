package retrylint.report

import com.fasterxml.jackson.databind.ObjectMapper
import retrylint.analysis.RetryLintAnalyzer
import java.nio.file.Path
import java.nio.file.Files
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AnalysisReportRendererTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val analyzer = RetryLintAnalyzer()

    @Test
    fun `text and JSON expose the same finding facts`() {
        val report = analyzeFixture("complete-example")
        val text = TextAnalysisReportRenderer().render(report)
        val json = ObjectMapper().readTree(JsonAnalysisReportRenderer().render(report))

        assertEquals(report.findings.size, json["findings"].size())
        report.findings.forEachIndexed { index, finding ->
            val jsonFinding = json["findings"][index]
            assertContains(text, "${finding.severity} ${finding.ruleId} — ${finding.title}")
            assertContains(text, finding.message)
            finding.callPath.forEach { assertContains(text, it) }
            assertEquals(finding.ruleId, jsonFinding["ruleId"].textValue())
            assertEquals(finding.severity.name.lowercase(), jsonFinding["severity"].textValue())
            assertEquals(finding.title, jsonFinding["title"].textValue())
            assertEquals(finding.message, jsonFinding["message"].textValue())
            assertEquals(finding.callPath, jsonFinding["callPath"].map { it.textValue() })
            assertEquals(finding.evidence.keys, jsonFinding["evidence"].fieldNames().asSequence().toSet())
        }
    }

    @Test
    fun `text and JSON expose the same completeness gaps`() {
        val report = analyzeFixture("rl002-unsupported")
        val text = TextAnalysisReportRenderer().render(report)
        val json = ObjectMapper().readTree(JsonAnalysisReportRenderer().render(report))

        assertEquals(report.completenessGaps.size, json["completenessGaps"].size())
        report.completenessGaps.forEachIndexed { index, gap ->
            val jsonGap = json["completenessGaps"][index]
            assertContains(text, gap.ruleId)
            assertContains(text, gap.reason)
            gap.operationId?.let { assertContains(text, it) }
            gap.callPath.forEach { assertContains(text, it) }
            assertEquals(gap.ruleId, jsonGap["ruleId"].textValue())
            assertEquals(gap.operationId, jsonGap["operationId"]?.textValue())
            assertEquals(gap.callPath, jsonGap["callPath"].map { it.textValue() })
            assertEquals(gap.reason, jsonGap["reason"].textValue())
        }
    }

    @Test
    fun `reports are checkout independent because configuration evidence paths are relative`() {
        val source = resourcePath("/fixtures/week5-mixed")
        val copies = listOf(temporaryDirectory.resolve("first"), temporaryDirectory.resolve("second"))
        copies.forEach { destination ->
            Files.walk(source).use { paths ->
                paths.forEach { current ->
                    val target = destination.resolve(source.relativize(current).toString())
                    if (Files.isDirectory(current)) Files.createDirectories(target) else Files.copy(current, target)
                }
            }
        }

        val reports = copies.map { analyzer.analyze(it.resolve("retrylint.yml")) }
        assertEquals(
            JsonAnalysisReportRenderer().render(reports[0]),
            JsonAnalysisReportRenderer().render(reports[1]),
        )
        reports[0].findings.forEach { finding ->
            finding.evidence.filterKeys { it.contains("configurationFile", ignoreCase = true) }
                .values.forEach { value ->
                    val paths = if (value is Iterable<*>) value else listOf(value)
                    paths.forEach { assertEquals(false, Path.of(it.toString()).isAbsolute) }
                }
        }
    }

    private fun analyzeFixture(name: String) = analyzer.analyze(
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI()),
    )

    private fun resourcePath(name: String): Path = Path.of(requireNotNull(javaClass.getResource(name)).toURI())
}
