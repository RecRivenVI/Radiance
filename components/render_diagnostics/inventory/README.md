# Offline static inventories

These tools implement the authorized A1–A8 frozen-period research. They do not run Minecraft,
load inspected mod classes, configure/build the product, download dependencies, benchmark,
stage or commit. Output is immutable per directory: use a new case name for a rerun.

Inputs are pinned by raw-byte SHA-256 (no EOL normalization). The historical reference tree
algorithm is recorded in its REFERENCE metadata and the A1 manifest. Bytecode source lines are
not decompiler-output lines. Method names, regex rows and bridge pointers are investigation leads,
not proof of interception, reachability or equivalent pixels.

## Locally available prerequisites (2026-10-03)

- BellSoft Java/Javac 21, Python 3.14, PyYAML 6.0.3.
- Gradle-cached ASM / ASM-tree 9.10.1, Gson 2.13.2; optional cached Vineflower 1.12.0.
- Read-only exact build/installed parents and `D:/Workspaces/References/minecraft-references`,
  explicitly identified by the user as the historical A1 root.

No installer/resolver is invoked. A missing prerequisite should be reported, not fetched silently.
Cached JAR identities and source provenance are in the external reference manifest.

## Reproduction (PowerShell, from Radiance)

Choose new output directories before running; the example names document this execution.

```powershell
$referenceManifest = 'D:\Workspaces\Artifacts\RadianceReference\20261003-static-a1\MANIFEST.json'
$inventoryJava = 'C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\java.exe'
$inventoryJavac = 'C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\javac.exe'
$inventoryCp = 'C:\Users\RavenYin\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.10.1\ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236\asm-9.10.1.jar;C:\Users\RavenYin\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.10.1\e244332a17564c1d1572449399a842de35881be2\asm-tree-9.10.1.jar;C:\Users\RavenYin\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.13.2\48b8230771e573b54ce6e867a9001e75977fe78e\gson-2.13.2.jar'

python components/render_diagnostics/inventory/acquire_references.py --radiance . --gradle-cache C:\Users\RavenYin\.gradle\caches --prism-instance 'E:\Minecraft\PrismLauncherDev\instances\Radiance 1.21.1-neoforge' --output D:\Workspaces\Artifacts\RadianceReference\20261003-static-a1 --java $inventoryJava --vineflower C:\Users\RavenYin\.gradle\caches\modules-2\files-2.1\org.vineflower\vineflower\1.12.0\85570609a0a5941a7d2918b6260b209de810f66f\vineflower-1.12.0.jar --historical-root D:\Workspaces\References\minecraft-references

& $inventoryJavac -cp $inventoryCp -d run/inventory-20261003/classes components/render_diagnostics/inventory/DrawInventory.java components/render_diagnostics/inventory/DrawInventoryTest.java
& $inventoryJava -cp "run/inventory-20261003/classes;$inventoryCp" DrawInventoryTest
& $inventoryJava -Xmx3G -cp "run/inventory-20261003/classes;$inventoryCp" DrawInventory $referenceManifest run/inventory-20261003/bytecode-v2
python components/render_diagnostics/inventory/static_inventory.py --radiance . --mcvr components/native_renderer --references $referenceManifest --output run/inventory-20261003/static-final
python components/render_diagnostics/inventory/enrich_inventory.py --radiance . --references $referenceManifest --bytecode run/inventory-20261003/bytecode-v2 --output run/inventory-20261003/draw-directory
python components/render_diagnostics/inventory/supplement_inventory.py --radiance . --mcvr components/native_renderer --references $referenceManifest --output run/inventory-20261003/supplement-complete
python -m unittest discover -s tools/inventory -p test_inventory.py -v
python components/render_diagnostics/inventory/verify_inventory.py --radiance . --mcvr components/native_renderer --case run/inventory-20261003 --references $referenceManifest --java $inventoryJava --javac $inventoryJavac --classpath $inventoryCp
```

The acquisition script can resume a partially acquired case but rejects an existing completed
manifest; use a new case for a fresh acquisition. Other output tools also reject existing directories. Earlier failed/incomplete outputs are
retained as such and are not silently merged into final evidence. The final static run includes
YAML module declarations; the earlier `static/` run omitted them and `static-v2/` preceded the
raw shader-hash correction. Use `static-final/` for final reports.

## Outputs and boundaries

| Tool | Outputs | Coverage boundary |
| --- | --- | --- |
| acquire_references.py | external MANIFEST, archives, source indexes | Binary parent identity distinct from authored-source equivalence; patched NeoForm sources not pristine Mojang source |
| DrawInventory.java | sites JSON/CSV, edges, archive/duplicate scope, limitations | Scans class bytes including nested archives, fields and lambda handles; does not resolve all virtual/reflected/native dispatch |
| static_inventory.py | raw first-party manifest, source candidates, shader lists/diffs, flags, ownership/events, options, JSON/YAML graphs and SDK pins | Regex/consumer leads; excludes extern source bodies from first-party scan; near duplicates defined by stated line threshold |
| enrich_inventory.py | component/source/bridge directory | UNKNOWN preserved; route pointer is not proof a particular invocation is translated |
| supplement_inventory.py | RenderState declaration context, JNI-name leads, enum bits, tracked resources/notices/CI, cached DLL hashes | JNI line count is not ABI parity; cached DLL list is not final JAR inclusion or licensing approval |
| DrawInventoryTest.java / test_inventory.py | Tool-only fixtures | Exercises scanner/input safety/repeatability; no product tests, Vulkan or runtime claims |
| verify_inventory.py | VALIDATION and EVIDENCE_MANIFEST | Recompiles scanner fixtures only; verifies frozen first-party bytes/Git/index, report headers and added link file targets; does not certify Markdown anchors or rendering |

Final `run/inventory-20261003/EVIDENCE_MANIFEST.json` hashes generated raw files, inventories the
tracked-tool sources and records the unchanged product snapshot. Machine-specific outputs are
ignored evidence; tools and relay/research reports are source candidates. No JAR/DLL/class/PDB,
world, log or copied reference belongs in those candidates.

See `docs/relay/2026-10-04-01-gpt-to-claude-static-tasks-summary.md` for findings and open decisions.

历史 A1–A8 的 `verify_inventory.py` 保留用于核对已归档的原始报告，不是迁移后仓库的验收入口。迁移后的工具测试由本组件的 `check` 执行。
