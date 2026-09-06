package retrylint.evaluation

import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 1) { "usage: Week9BenchmarkMain <evaluation/week9>" }
    val root = Path.of(args.single()).toAbsolutePath().normalize()
    val runner = Week9BenchmarkRunner()
    val run = runner.run(root)
    runner.write(run, root)
    println(
        "Week 9 benchmark: ${run.summary.executedCaseCount} executed, ${run.summary.skippedCaseCount} skipped; " +
            "TP=${run.summary.overall.tp}, FP=${run.summary.overall.fp}, FN=${run.summary.overall.fn}",
    )
}
