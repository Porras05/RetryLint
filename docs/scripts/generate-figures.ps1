[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$culture = [System.Globalization.CultureInfo]::InvariantCulture
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$figureDir = Join-Path $repoRoot 'docs\figures'
New-Item -ItemType Directory -Force -Path $figureDir | Out-Null

function Write-Utf8File([string] $Path, [string] $Content) {
    [System.IO.File]::WriteAllText($Path, $Content.Trim() + "`n", [System.Text.UTF8Encoding]::new($false))
}

function F([double] $Value, [string] $Format = '0.###') {
    return $Value.ToString($Format, $culture)
}

$week8Path = Join-Path $repoRoot 'evaluation\week8-results.json'
$week9Path = Join-Path $repoRoot 'evaluation\week9\results\summary.json'
$week10Path = Join-Path $repoRoot 'evaluation\week10\results\scalability-results.json'
$week8 = Get-Content -LiteralPath $week8Path -Raw | ConvertFrom-Json
$week9 = Get-Content -LiteralPath $week9Path -Raw | ConvertFrom-Json
$week10 = Get-Content -LiteralPath $week10Path -Raw | ConvertFrom-Json

if ($week8.observedRuns.Count -ne $week8.runs) { throw 'Week 8 run count is inconsistent.' }
foreach ($run in $week8.observedRuns) {
    if ($run.Checkout -ne $week8.expectedCounts.checkout -or
        $run.Orders -ne $week8.expectedCounts.orders -or
        $run.Payments -ne $week8.expectedCounts.payments -or
        $run.Bank -ne $week8.expectedCounts.bank -or
        $run.Result -ne 'PASS') {
        throw 'Week 8 contains an observation that does not match the recorded expected counts.'
    }
}

$architecture = @'
<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="360" viewBox="0 0 1120 360" role="img" aria-labelledby="title desc">
  <title id="title">RetryLint processing pipeline</title>
  <desc id="desc">Static architecture diagram derived from the frozen RetryLint source packages.</desc>
  <style>.box{fill:#f7f9fc;stroke:#344563;stroke-width:2}.rule{fill:#e9f2ff;stroke:#1967b3;stroke-width:2}.label{font:600 15px system-ui,sans-serif;fill:#172b4d}.small{font:13px system-ui,sans-serif;fill:#42526e}.arrow{stroke:#596780;stroke-width:2;marker-end:url(#a)}</style>
  <defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10Z" fill="#596780"/></marker></defs>
  <rect class="box" x="20" y="135" width="130" height="70" rx="8"/><text class="label" x="85" y="165" text-anchor="middle">retrylint.yml</text><text class="small" x="85" y="186" text-anchor="middle">+ service YAML</text>
  <line class="arrow" x1="150" y1="170" x2="185" y2="170"/>
  <rect class="box" x="190" y="105" width="160" height="130" rx="8"/><text class="label" x="270" y="137" text-anchor="middle">Input + graph</text><text class="small" x="270" y="161" text-anchor="middle">parse manifest</text><text class="small" x="270" y="182" text-anchor="middle">validate topology</text><text class="small" x="270" y="203" text-anchor="middle">topological order</text>
  <line class="arrow" x1="350" y1="170" x2="385" y2="170"/>
  <rect class="box" x="390" y="105" width="175" height="130" rx="8"/><text class="label" x="477" y="137" text-anchor="middle">Configuration</text><text class="small" x="477" y="161" text-anchor="middle">caller ownership</text><text class="small" x="477" y="182" text-anchor="middle">defaults + inheritance</text><text class="small" x="477" y="203" text-anchor="middle">known / gap</text>
  <line class="arrow" x1="565" y1="170" x2="600" y2="170"/>
  <rect class="rule" x="605" y="35" width="145" height="65" rx="8"/><text class="label" x="677" y="63" text-anchor="middle">RL001</text><text class="small" x="677" y="84" text-anchor="middle">amplification</text>
  <rect class="rule" x="605" y="137" width="145" height="65" rx="8"/><text class="label" x="677" y="165" text-anchor="middle">RL002</text><text class="small" x="677" y="186" text-anchor="middle">timeout budget</text>
  <rect class="rule" x="605" y="239" width="145" height="65" rx="8"/><text class="label" x="677" y="267" text-anchor="middle">RL003</text><text class="small" x="677" y="288" text-anchor="middle">unsafe retry</text>
  <line class="arrow" x1="750" y1="68" x2="785" y2="145"/><line class="arrow" x1="750" y1="170" x2="785" y2="170"/><line class="arrow" x1="750" y1="271" x2="785" y2="195"/>
  <rect class="box" x="790" y="105" width="135" height="130" rx="8"/><text class="label" x="857" y="148" text-anchor="middle">AnalysisReport</text><text class="small" x="857" y="174" text-anchor="middle">findings</text><text class="small" x="857" y="195" text-anchor="middle">completeness gaps</text>
  <line class="arrow" x1="925" y1="170" x2="960" y2="170"/>
  <rect class="box" x="965" y="105" width="135" height="130" rx="8"/><text class="label" x="1032" y="146" text-anchor="middle">CLI</text><text class="small" x="1032" y="171" text-anchor="middle">text / JSON</text><text class="small" x="1032" y="192" text-anchor="middle">exit code</text>
</svg>
'@
Write-Utf8File (Join-Path $figureDir 'architecture.svg') $architecture

$names = @('checkout', 'orders', 'payments', 'bank')
$properties = @('checkout', 'orders', 'payments', 'bank')
$nodes = for ($i = 0; $i -lt $names.Count; $i++) {
    $x = 40 + 245 * $i
    $count = $week8.expectedCounts.($properties[$i])
    "  <rect class=`"box`" x=`"$x`" y=`"105`" width=`"170`" height=`"90`" rx=`"10`"/><text class=`"name`" x=`"$($x+85)`" y=`"140`" text-anchor=`"middle`">$($names[$i])</text><text class=`"count`" x=`"$($x+85)`" y=`"175`" text-anchor=`"middle`">$count</text>"
}
$arrows = for ($i = 0; $i -lt 3; $i++) {
    $x1 = 210 + 245 * $i
    $x2 = 280 + 245 * $i
    $sourceCount = [double]$week8.expectedCounts.($properties[$i])
    $targetCount = [double]$week8.expectedCounts.($properties[$i + 1])
    $ratio = $targetCount / $sourceCount
    "  <line class=`"arrow`" x1=`"$x1`" y1=`"150`" x2=`"$x2`" y2=`"150`"/><text class=`"times`" x=`"$([int](($x1+$x2)/2))`" y=`"136`" text-anchor=`"middle`">x$(F $ratio)</text>"
}
$week8Svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="960" height="280" viewBox="0 0 960 280" role="img" aria-labelledby="title desc">
  <title id="title">Week 8 retry amplification chain</title>
  <desc id="desc">Source: evaluation/week8-results.json. All $($week8.runs) recorded runs matched the displayed counts; predicted multiplier $($week8.predictedMultiplier).</desc>
  <style>.box{fill:#eef5ff;stroke:#1967b3;stroke-width:2}.name{font:600 18px system-ui,sans-serif;fill:#172b4d}.count{font:700 28px system-ui,sans-serif;fill:#0747a6}.times{font:600 15px system-ui,sans-serif;fill:#42526e}.arrow{stroke:#596780;stroke-width:2;marker-end:url(#a)}.note{font:13px system-ui,sans-serif;fill:#596780}</style>
  <defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10Z" fill="#596780"/></marker></defs>
$($nodes -join "`n")
$($arrows -join "`n")
  <text class="note" x="480" y="235" text-anchor="middle">Static prediction and controlled runtime observation matched in $($week8.runs)/$($week8.runs) recorded runs</text>
  <text class="note" x="480" y="258" text-anchor="middle">Source: evaluation/week8-results.json</text>
</svg>
"@
Write-Utf8File (Join-Path $figureDir 'week8-amplification.svg') $week8Svg

$total = [int]$week9.totalCaseCount
$seedWidth = 720 * ([double]$week9.seedCount / $total)
$mutantWidth = 720 - $seedWidth
$week9Svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="360" viewBox="0 0 900 360" role="img" aria-labelledby="title desc">
  <title id="title">Week 9 mutation benchmark composition and results</title>
  <desc id="desc">Source: evaluation/week9/results/summary.json. $($week9.seedCount) seeds, $($week9.mutantCount) mutants, TP $($week9.overall.tp), FP $($week9.overall.fp), FN $($week9.overall.fn).</desc>
  <style>.h{font:700 21px system-ui,sans-serif;fill:#172b4d}.label{font:600 16px system-ui,sans-serif;fill:#172b4d}.value{font:700 26px system-ui,sans-serif;fill:#0747a6}.small{font:13px system-ui,sans-serif;fill:#596780}.seed{fill:#79c2ff}.mutant{fill:#6554c0}.metric{fill:#f4f5f7;stroke:#97a0af}</style>
  <text class="h" x="450" y="38" text-anchor="middle">Controlled reviewed corpus: $total cases</text>
  <rect class="seed" x="90" y="70" width="$(F $seedWidth)" height="55"/><rect class="mutant" x="$(F (90+$seedWidth))" y="70" width="$(F $mutantWidth)" height="55"/>
  <text class="label" x="$(F (90+$seedWidth/2))" y="104" text-anchor="middle">$($week9.seedCount) seeds</text><text class="label" x="$(F (90+$seedWidth+$mutantWidth/2))" y="104" text-anchor="middle" fill="white">$($week9.mutantCount) mutants</text>
  <rect class="metric" x="90" y="165" width="210" height="105" rx="8"/><text class="label" x="195" y="195" text-anchor="middle">TP / FP / FN</text><text class="value" x="195" y="238" text-anchor="middle">$($week9.overall.tp) / $($week9.overall.fp) / $($week9.overall.fn)</text>
  <rect class="metric" x="345" y="165" width="210" height="105" rx="8"/><text class="label" x="450" y="195" text-anchor="middle">Precision / Recall</text><text class="value" x="450" y="238" text-anchor="middle">$(F $week9.overall.precision '0.000') / $(F $week9.overall.recall '0.000')</text>
  <rect class="metric" x="600" y="165" width="210" height="105" rx="8"/><text class="label" x="705" y="195" text-anchor="middle">F1</text><text class="value" x="705" y="238" text-anchor="middle">$(F $week9.overall.f1 '0.000')</text>
  <text class="small" x="450" y="310" text-anchor="middle">Applies to this controlled corpus; $($week9.executedCaseCount) executed, $($week9.skippedCaseCount) skipped</text>
  <text class="small" x="450" y="334" text-anchor="middle">Source: evaluation/week9/results/summary.json</text>
</svg>
"@
Write-Utf8File (Join-Path $figureDir 'week9-benchmark.svg') $week9Svg

$cases = @($week10.cases)
$maxP95Ms = ($cases | ForEach-Object { $_.timings.p95Nanoseconds / 1000000.0 } | Measure-Object -Maximum).Maximum
$yMax = [Math]::Ceiling($maxP95Ms * 1.15 / 5) * 5
$plotLeft = 80.0; $plotTop = 55.0; $plotWidth = 700.0; $plotHeight = 275.0
$minLog = [Math]::Log10(($cases | Measure-Object operationCount -Minimum).Minimum)
$maxLog = [Math]::Log10(($cases | Measure-Object operationCount -Maximum).Maximum)
function PlotX([double] $Operations) { return $plotLeft + (([Math]::Log10($Operations) - $minLog) / ($maxLog - $minLog)) * $plotWidth }
function PlotY([double] $Milliseconds) { return $plotTop + $plotHeight - ($Milliseconds / $yMax) * $plotHeight }
$medianPoints = @($cases | ForEach-Object { "$(F (PlotX $_.operationCount)),$(F (PlotY ($_.timings.medianNanoseconds/1000000.0)))" }) -join ' '
$p95Points = @($cases | ForEach-Object { "$(F (PlotX $_.operationCount)),$(F (PlotY ($_.timings.p95Nanoseconds/1000000.0)))" }) -join ' '
$ticks = for ($v = 0; $v -le $yMax; $v += 5) {
    $y = PlotY $v
    "  <line class=`"grid`" x1=`"$plotLeft`" y1=`"$(F $y)`" x2=`"$($plotLeft+$plotWidth)`" y2=`"$(F $y)`"/><text class=`"small`" x=`"$($plotLeft-12)`" y=`"$(F ($y+4))`" text-anchor=`"end`">$v</text>"
}
$markers = foreach ($case in $cases) {
    $x = PlotX $case.operationCount
    $median = $case.timings.medianNanoseconds / 1000000.0
    $p95 = $case.timings.p95Nanoseconds / 1000000.0
    "  <circle class=`"median`" cx=`"$(F $x)`" cy=`"$(F (PlotY $median))`" r=`"5`"/><circle class=`"p95`" cx=`"$(F $x)`" cy=`"$(F (PlotY $p95))`" r=`"5`"/><text class=`"small`" x=`"$(F $x)`" y=`"$($plotTop+$plotHeight+25)`" text-anchor=`"middle`">$($case.operationCount)</text>"
}
$week10Svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="900" height="430" viewBox="0 0 900 430" role="img" aria-labelledby="title desc">
  <title id="title">Week 10 in-process analyzer latency</title>
  <desc id="desc">Source: evaluation/week10/results/scalability-results.json. Median and nearest-rank p95 from retained raw timings.</desc>
  <style>.axis{stroke:#344563;stroke-width:2}.grid{stroke:#dfe1e6;stroke-width:1}.medianLine{fill:none;stroke:#0052cc;stroke-width:3}.p95Line{fill:none;stroke:#de350b;stroke-width:3}.median{fill:#0052cc}.p95{fill:#de350b}.label{font:600 15px system-ui,sans-serif;fill:#172b4d}.small{font:12px system-ui,sans-serif;fill:#596780}</style>
$($ticks -join "`n")
  <line class="axis" x1="$plotLeft" y1="$plotTop" x2="$plotLeft" y2="$($plotTop+$plotHeight)"/><line class="axis" x1="$plotLeft" y1="$($plotTop+$plotHeight)" x2="$($plotLeft+$plotWidth)" y2="$($plotTop+$plotHeight)"/>
  <polyline class="medianLine" points="$medianPoints"/><polyline class="p95Line" points="$p95Points"/>
$($markers -join "`n")
  <text class="label" x="430" y="390" text-anchor="middle">Operations (log-scaled positions)</text><text class="label" x="20" y="205" text-anchor="middle" transform="rotate(-90 20 205)">Latency (ms)</text>
  <line class="medianLine" x1="675" y1="25" x2="710" y2="25"/><text class="small" x="718" y="29">Median</text><line class="p95Line" x1="780" y1="25" x2="815" y2="25"/><text class="small" x="823" y="29">P95</text>
  <text class="small" x="450" y="417" text-anchor="middle">Source: evaluation/week10/results/scalability-results.json; in-process analyzer only</text>
</svg>
"@
Write-Utf8File (Join-Path $figureDir 'week10-scalability.svg') $week10Svg

Write-Output "Generated 4 SVG figures in $figureDir"
