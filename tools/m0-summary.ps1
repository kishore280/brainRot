# Summarises an M0 capture: for every scrolling view Instagram reported, does it carry indices?
# Usage: pwsh tools/m0-summary.ps1 captures/capture-<ts>.jsonl
param([Parameter(Mandatory)][string]$Path)

$lines = Get-Content $Path
$meta = $lines | Where-Object { $_ -match '"SPIKE_CONNECTED"' } | Select-Object -First 1 | ConvertFrom-Json
if ($meta) { "Device: $($meta.device)  SDK $($meta.sdk)  Instagram $($meta.igVersion)`n" }

$scrolls = $lines | Where-Object { $_ -match '"TYPE_VIEW_SCROLLED"' } | ForEach-Object { $_ | ConvertFrom-Json }
if (-not $scrolls) { "No TYPE_VIEW_SCROLLED events at all -> design section 17, Risk 1."; return }

$scrolls | Group-Object { "$($_.src.id) | $($_.src.cls)" } | Sort-Object Count -Descending | ForEach-Object {
    $g = $_.Group
    $withTo    = @($g | Where-Object { $_.to -ge 0 }).Count
    $withCount = @($g | Where-Object { $_.count -ge 0 }).Count
    $withDy    = @($g | Where-Object { $_.dy -ne 0 }).Count
    $childRows = @($g | Where-Object { $_.children | Where-Object { $null -ne $_.itemRow } }).Count

    # A settled page is one where only a single item is visible (from == to).
    $settled = @()
    foreach ($s in $g) {
        if ($s.to -ge 0 -and $s.from -eq $s.to -and ($settled.Count -eq 0 -or $settled[-1] -ne $s.to)) { $settled += $s.to }
    }
    $toChanges = @()
    foreach ($s in $g) {
        if ($s.to -ge 0 -and ($toChanges.Count -eq 0 -or $toChanges[-1] -ne $s.to)) { $toChanges += $s.to }
    }
    $height = if ($g[0].src.bounds) { $b = $g[0].src.bounds -split ','; [int]$b[3] - [int]$b[1] } else { '?' }

    "== $($_.Name)"
    "   events: $($_.Count)   height: $height px"
    "   toIndex populated:   $withTo / $($_.Count)"
    "   itemCount populated: $withCount / $($_.Count)   (values: $((($g | ForEach-Object { $_.count } | Sort-Object -Unique) -join ',')))"
    "   scrollDeltaY != 0:   $withDy / $($_.Count)"
    "   children w/ itemRow: $childRows / $($_.Count)"
    "   toIndex changes:     $($toChanges -join ' ')"
    "   settled (from==to):  $($settled -join ' ')"
    ""
}
