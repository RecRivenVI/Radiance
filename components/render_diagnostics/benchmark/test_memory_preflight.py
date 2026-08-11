import json
from pathlib import Path
import subprocess
import unittest


class MemoryPreflightTest(unittest.TestCase):
    def evaluate(self, script):
        helper = str(Path(__file__).with_name('MemoryAttributionPreflight.ps1')).replace("'", "''")
        output = subprocess.check_output(['pwsh', '-NoProfile', '-Command', f". '{helper}'; {script}"], text=True)
        return json.loads(output)

    def test_only_explicit_isolated_memory_runs_can_use_load_exception(self):
        result = self.evaluate("""
        $launch=[pscustomobject]@{classification='MEMORY_LIFECYCLE';allowCompetingLoadForMemoryAttribution=$true}
        $results=@(
            (Test-MemoryAttributionPreflight $launch $true 0 8192),
            (Test-MemoryAttributionPreflight $launch $false 0 8192),
            (Test-MemoryAttributionPreflight $launch $true 1 8192),
            (Test-MemoryAttributionPreflight $launch $true 0 8191),
            (Test-MemoryAttributionPreflight $launch $true 0 $null))
        $launch.classification='PERFORMANCE'
        $results+=(Test-MemoryAttributionPreflight $launch $true 0 16384)
        $results|ConvertTo-Json -Compress
        """)
        self.assertEqual([True, False, False, False, False, False], result)

    def test_memory_classification_alone_does_not_relax_the_gate(self):
        result = self.evaluate("""
        $launch=[pscustomobject]@{classification='MEMORY_LIFECYCLE'}
        Test-MemoryAttributionPreflight $launch $true 0 16384|ConvertTo-Json -Compress
        """)
        self.assertFalse(result)

    def test_compatibility_exception_is_explicit_and_resource_bounded(self):
        result = self.evaluate("""
        $launch=[pscustomobject]@{classification='NEOFORGE_MINIMUM_COMPATIBILITY';allowModerateLoadForCompatibility=$true}
        $results=@(
            (Test-CompatibilityPreflight $launch $true 0 8192 20 50),
            (Test-CompatibilityPreflight $launch $false 0 8192 20 50),
            (Test-CompatibilityPreflight $launch $true 1 8192 20 50),
            (Test-CompatibilityPreflight $launch $true 0 8191 20 50),
            (Test-CompatibilityPreflight $launch $true 0 $null 20 50),
            (Test-CompatibilityPreflight $launch $true 0 8192 21 50),
            (Test-CompatibilityPreflight $launch $true 0 8192 20 51),
            (Test-CompatibilityPreflight $launch $true 0 8192 $null 50),
            (Test-CompatibilityPreflight $launch $true 0 8192 20 $null))
        $launch.classification='PERFORMANCE'
        $results+=(Test-CompatibilityPreflight $launch $true 0 16384 1 1)
        $launch.classification='NEOFORGE_MINIMUM_COMPATIBILITY'
        $launch.allowModerateLoadForCompatibility=$false
        $results+=(Test-CompatibilityPreflight $launch $true 0 16384 1 1)
        $results|ConvertTo-Json -Compress
        """)
        self.assertEqual([True] + [False] * 10, result)


if __name__ == '__main__':
    unittest.main()
