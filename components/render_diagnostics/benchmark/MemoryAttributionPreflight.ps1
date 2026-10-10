function Test-MemoryAttributionPreflight($launch, [bool]$isolated, [int]$otherClients, $freeMiB) {
    # Explicit memory-only runs may tolerate unrelated compute load. Never turn this
    # into a quiet/performance verdict, or permit another client / low VRAM headroom.
    return $isolated -and $launch.classification -eq 'MEMORY_LIFECYCLE' -and
        $launch.allowCompetingLoadForMemoryAttribution -eq $true -and
        $otherClients -eq 0 -and $null -ne $freeMiB -and $freeMiB -ge 8192
}

function Test-CompatibilityPreflight($launch, [bool]$isolated, [int]$otherClients, $freeMiB, $cpuPct, $gpuPct) {
    # Bounded loader qualification is not a performance sample. Explicitly cap unrelated load,
    # retain the no-other-client/VRAM checks, and never apply this to benchmark classifications.
    return $isolated -and $launch.classification -eq 'NEOFORGE_MINIMUM_COMPATIBILITY' -and
        $launch.allowModerateLoadForCompatibility -eq $true -and
        $otherClients -eq 0 -and $null -ne $freeMiB -and $freeMiB -ge 8192 -and
        $null -ne $cpuPct -and $cpuPct -le 20 -and
        $null -ne $gpuPct -and $gpuPct -le 50
}
