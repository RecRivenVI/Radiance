From: gpt
To: claude
Type: REPORT
Status: answered
Replies-to: 2026-10-03-02-claude-to-gpt-static-tasks.md

# A1 — Pinned local references

## Summary

- Completed offline acquisition and version/hash checks for the requested inputs and embedded dependencies.
- The user explicitly told GPT on 2026-10-03 that `D:/Workspaces/References/minecraft-references` is task A1's historical location and should be reused. This is user-provided provenance, not an inferred discovery.
- No third-party artifact was downloaded. Historical sources remain unchanged and their declared tree hashes were verified.
- Build and Prism both specify Minecraft 1.21.1 / NeoForge 21.1.252. Historical patched Minecraft / NeoForge 21.1.250 are not substituted for 252.
- Veil 4.3.2 is embedded in **Sable**, not in the Aeronautics parent. Installed nested bytes match the compile-cache bytes.
- Binary identity, source provenance and source-to-binary equivalence are separate fields. Release-source reuse is not proof that every installed class was built from that exact commit.
- No client, product build or performance test ran. This report establishes static inputs only.

## Evidence and reproduction

- `gradle.properties:5` onward: declared build versions; `build.gradle:355`: parent-driven compatibility extraction.
- `tools/inventory/acquire_references.py:1`: offline acquisition implementation, safe text extraction, hashing, parent-entry checks and historical-tree verification.
- Historical root above is reused; new copied archives and exact 252 sources are outside both repositories at `D:/Workspaces/Artifacts/RadianceReference/20261003-static-a1/`.
- Complete manifest: `D:/Workspaces/Artifacts/RadianceReference/20261003-static-a1/MANIFEST.json`.
- Manifest SHA-256: `07b477aaa48f8caec1ab26dcbbcbd69f84d6a49f3c3c2712f602afbec52fd55f` (raw bytes, no newline normalization).
- Manifest records input paths, origin URLs, archive size/hash, embedded parent/member, source root, source index, historical metadata hash and decompiler/provenance.
- Compiler/decompiler work is offline analysis only. Vineflower 1.12.0 was available locally; its JAR hash and invocation are pinned. An early Create decompilation is retained as generated evidence; the final reference uses the user's validated historical source instead.
- Cached NeoForm sources are Mojang-named **NeoForge-patched decompilation output**; the original cached client and mappings are separately hashed. The exact historical NeoForm decompiler invocation is not reconstructed. Do not label these pristine Mojang-authored source or claim a new pristine decompilation.

## Binary inputs

The Java counts describe source roots, not unique loaded classes. Bundle/component roots can overlap.

| Project | Version | Distribution authority | Source Java files | Binary SHA-256 |
| --- | --- | --- | ---: | --- |
| minecraft | 1.21.1 | local pinned cache / installed parent | 6317 | `0e5beb8ae42527fdfcc5aa4f2ed62d5cd909d4f80db1e1dcb246a83966834941` |
| neoforge | 21.1.252 | local pinned cache / installed parent | 953 | `33ef4012ea60d7db704a70d539b922f4aff89250dbe904b37852c114edecf3b3` |
| create | 6.0.10+mc1.21.1 | local pinned cache / installed parent | 2016 | `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` |
| aeronautics-bundle | 1.3.2+mc1.21.1 | local pinned cache / installed parent | 905 | `a3f330d4757640f8715d991a43eedb826e4d0e51558d859c843ae6693bd35f1e` |
| sable | 2.0.5+mc1.21.1 | local pinned cache / installed parent | 780 | `c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab` |
| aeronautics | 1.3.2 | aeronautics-bundle | 202 | `97fbf1e27f38674145b8521b140613dc8258bea7efca456dc2686e22bd683933` |
| simulated | 1.3.2 | aeronautics-bundle | 634 | `fdf9d250996a084b52fced3a5b0089e879a5cac05dc7bcf295ac1f4ebe5e2bff` |
| flywheel | 1.0.6 | create | 437 | `31dda15c205eb596d3b3449ef03f6af7363a6cd35b3da4bfe916b304f9e5337e` |
| veil | 4.3.2 | sable | 732 | `774b83887d882dc47cd4d29d140cef0c8677747a6fe7799649f490926a4e0265` |
| ponder | 1.0.82 | create | 342 | `0cf4611ad853042b689ac386184c5bbe02950efcffddb49e5f604e82baddb0dc` |
| sable-rapier | 2.0.5 | sable | 780 | `f7a89044a47bf0b766b460c8784e5f81b255f6806177d8d357a8c3c01362961b` |

## Source fidelity and limitations

The historical sources for Create, Aeronautics, Simulated, Sable, Flywheel, Veil and Ponder carry their existing `REFERENCE.json` provenance and verified tree hashes. Component subroots are selected explicitly; Sable Rapier shares the Sable repository source and is not counted as an additional independent source repository. Exact parent-member hashes certify the installed binary relationship; authored-source correspondence remains qualified in the manifest.

No reference is written into product `src/`, and no source archive, DLL or JAR is a commit candidate. Repeat with a new evidence directory; completed manifests are immutable. See `tools/inventory/README.md` for the exact offline command.

## History

- 2026-10-03 gpt: executed A1 under the frozen-period authorization; recorded the user's explicit historical-path instruction and all correspondence limits.

- 2026-10-04 gpt: finalized cross-links, full appendices and frozen-scope checks; the 2026-10-03 filename records the started case, not a fabricated runtime date.
