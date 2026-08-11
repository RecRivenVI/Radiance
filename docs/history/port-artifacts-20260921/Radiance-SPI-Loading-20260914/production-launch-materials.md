# Bounded production-launch material audit

Date: 2026-09-14

No client was launched and no launcher, instance, account, or configuration file was
modified.

## Reusable installed client

- Portable launcher root: E:\Minecraft\PrismLauncher
- Launcher binary: E:\Minecraft\PrismLauncher\prismlauncher.exe
- File version: 11.1.0.0
- Existing instance ID: Create Aeronautics NeoForge 1.21.1
- Instance metadata:
  - Minecraft 1.21.1
  - NeoForge 21.1.248
  - LWJGL 3.3.3
- NeoForge metadata:
  - mainClass: io.github.zekerzhayard.forgewrapper.installer.Main
  - launchTarget: forgeclient
  - FML: 4.0.43
  - NeoForm: 20240808.144430

All 45 artifacts declared by
E:\Minecraft\PrismLauncher\meta\net.neoforged\21.1.248.json were present under the
portable libraries directory and matched their declared SHA-1.

Additional exact checks:

| Material | SHA-1 | Result |
| --- | --- | --- |
| Minecraft 1.21.1 client JAR | 30c73b1c5da787909b2f73340419fdf13b9def88 | PASS |
| Minecraft asset index 17 | cdc3b02414acf6de9e3c1cc21ad494b022a2dabc | PASS |
| ForgeWrapper prism-2026-08-01 | 852b7e59748da1512d40e38407eadb1f0031a996 | PASS |
| BootstrapLauncher 2.0.2 | 1a2d076cbc33b0520cbacd591224427b2a20047d | present |
| NeoForge 21.1.248 client JAR | bab98816cfdbccc1e9076872771ef81f39afbcc9 | present |

The existing instance's minecraft\config\fml.toml currently has
earlyWindowControl=true and earlyWindowProvider="fmlearlywindow". This is suitable for
the Radiance bootstrap's fresh-default selection behavior.

The existing instance is not a clean single-mod acceptance instance:
zume-1.2.2.jar is active. Other listed mod files are disabled.

## Minimal isolated acceptance flow

1. In Prism Launcher, copy Create Aeronautics NeoForge 1.21.1 into a dedicated
   acceptance instance without copying mods or saves. The installed metadata and
   libraries can be reused; no NeoForge installer run or client patch preparation is
   needed.
2. Configure that instance to use JDK 21.
3. Put only the final outer Radiance.jar in the copied instance's minecraft\mods
   directory.
4. Leave the fresh FML defaults intact. The outer GraphicsBootstrapper will select
   provider radiance while still respecting earlyWindowControl=false if the user
   explicitly disables it.
5. Launch by instance-folder ID:

       E:\Minecraft\PrismLauncher\prismlauncher.exe -d E:\Minecraft\PrismLauncher -l "<instance ID>"

Prism's official CLI documentation defines -d/--dir as the application root and
-l/--launch as an instance-folder ID:
https://prismlauncher.org/wiki/getting-started/command-line-interface/

Prism's official data-location documentation confirms portable installations keep
assets, instances, libraries, and metadata under the portable launcher directory:
https://prismlauncher.org/wiki/getting-started/data-location/
