# resource-lifetime-keepalive-v2 product manifest

Build source:

- Radiance: `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`, branch `1.21.1-neoforge`, HEAD `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`, dirty working tree preserved.
- MCVR: `D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`, branch `develop`, HEAD `9905c81b1999f5845bf66d13501d371c16adf561`, dirty working tree preserved.
- Focused source change: `MCVR/src/core/vulkan/descriptor.hpp` and `descriptor.cpp`.
- No Git staging, commit, push, reset, or history rewrite.

## Product hashes

| Artifact | Size | SHA-256 |
|---|---:|---|
| `Radiance.jar` | 146642348 | `CEC66840C01B96EF4ED203810E56C80E18B676A186AD8AE99F2B94BF4535A8D7` |
| `core.dll` | 30967808 | `44D3B6F8F2013D7B5ACFCFC06331FE09FE4DD5A5FECC1E9BD923DED87CE9A112` |
| `core.pdb` | 45764608 | `A4F994D67B4466AE09278864E254EA640153490A9FADD46B64DBC8A84AB6B0FD` |

The embedded `core.dll` in `Radiance.jar` is 30967808 bytes and has SHA-256 `44D3B6F8F2013D7B5ACFCFC06331FE09FE4DD5A5FECC1E9BD923DED87CE9A112`, matching the standalone Release DLL and the installed/resource copies.

## Focused source snapshot

| File | Size | SHA-256 |
|---|---:|---|
| `Source/resource-lifetime-keepalive-v2/descriptor.hpp` | 6642 | `36EA19963334B820A952E864DD1298614A82DFE793E7D0B97D834D2644CB98F1` |
| `Source/resource-lifetime-keepalive-v2/descriptor.cpp` | 18815 | `A5B54CF216BEBFA0B3C3CCB0AD3DCEC21085DBE5B8EC0E41769A7A8F2EBE587D` |

The corrected implementation keeps every resource generation written to a descriptor-table slot until that descriptor table retires, deduplicated by `shared_ptr` control block. This is required because the relevant descriptor bindings permit update-after-bind; replacing the keepalive immediately on a later write could release an older generation still referenced by a bound, not-yet-submitted table. The table remains bounded by the existing frame-retainer/fence lifetime and is not a global or permanent resource cache.

## Verification and boundary

- MCVR Release build: PASS.
- Existing native CTest: 24/24 PASS.
- MCVR INSTALL: PASS.
- Radiance `distributedJar --no-daemon`: PASS.
- PE CodeView: RSDS `{C563B7F0-CC62-4BD8-AD56-A4067DC94C82}`, age `10`, paired PDB path verified with the installed Visual Studio `dumpbin /headers`.
- This is a repair-validation checkpoint. It still contains default-off D-STATIC-09 draw-state diagnostics so a later real-client comparison can distinguish the repair effect. It has not yet been loaded by a real Minecraft client.
- Final diagnostic cleanup and a diagnostic-free release build remain pending until the repaired real-client comparison is completed.
