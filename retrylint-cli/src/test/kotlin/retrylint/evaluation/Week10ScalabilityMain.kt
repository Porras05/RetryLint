package retrylint.evaluation

import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 1) { "usage: Week10ScalabilityMain <evaluation/week10>" }
    val root = Path.of(args.single()).toAbsolutePath().normalize()
    val runner = Week10ScalabilityRunner()
    val result = runner.run(root)
    runner.write(result, root)
    println("Week 10 scalability: ${result.cases.size} sizes, ${result.cases.sumOf { it.measuredRunCount }} measured runs, all correct")
}
