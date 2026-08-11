# resource-lifetime-keepalive product manifest

Build source:

- Radiance: `D:\Workspaces\Repositories\GitHub\RecRivenVI\Radiance`, branch `1.21.1-neoforge`, HEAD `414d8e330a2fc6cb1e8630cc95f2302b2b97a0e8`, dirty working tree preserved.
- MCVR: `D:\Workspaces\Repositories\GitHub\RecRivenVI\MCVR`, branch `develop`, HEAD `9905c81b1999f5845bf66d13501d371c16adf561`, dirty working tree preserved.
- Focused new source change: `src/core/vulkan/descriptor.hpp` and `descriptor.cpp`, descriptor-table resource keepalive. No Git staging, commit, push, reset or history rewrite.

| Artifact | Size | SHA-256 |
|---|---:|---|
| `Radiance.jar` | 146642349 | `9E3B47C9C363374CC99A759BA47D3319E0AD82F7540BD0AAD7251A1D5DF04CD5` |
| `core.dll` | 30967808 | `48DEA3775CE149D618598146EDFF63EFA1B79150587E0226B14DC9FE873C0C21` |
| `core.pdb` | 45764608 | `104F8C0DECB45A4075F2CBAE8F89748D6DD1EDB24BDF4504EBC9CD838EBC5547` |

The embedded `core.dll` in `Radiance.jar` has the same size and SHA-256 as the standalone Release DLL. The product is a repair-validation checkpoint: it still contains the default-off D-STATIC-09 draw-state diagnostic code for the next real-client comparison, and it has not yet been loaded by a real Minecraft client. Final delivery requires a later diagnostic cleanup rebuild after the repair comparison.

The standalone DLL CodeView record points to the paired PDB path and reports RSDS GUID `{C563B7F0-CC62-4BD8-AD56-A4067DC94C82}`, age `9` (verified with the installed Visual Studio `dumpbin /headers`).

## Superseded

This historical candidate is superseded by `Products/resource-lifetime-keepalive-v2`. Its per-slot keepalive vector replaced the prior generation on descriptor overwrite; after accounting for the enabled update-after-bind semantics, that retention was too weak for the intended lifetime guarantee. The candidate is preserved as evidence and must not be used for the next real-client comparison.
