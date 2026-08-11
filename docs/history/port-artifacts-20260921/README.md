# Historical Port evidence archive

This directory contains the compact, human-authored record recovered from the external Radiance and
MCVR Port investigation folders on 2026-09-21.

The original external folders mixed useful conclusions with reproducible source snapshots, build
outputs, copied game instances, JAR/DLL/PDB files, logs, frame captures, and two native minidumps.
The selected reports and small acceptance records were copied here with their original
artifact-relative paths. `MANIFEST.csv` records the original absolute path, archive-relative path,
byte count, and SHA-256 of every migrated file.

Use these records as historical evidence. They describe the state observed at the recorded time and
may refer to raw evidence that was intentionally recycled during cleanup. Current behavior,
implementation, deployment, and pending work belong in the parent
[`DEVELOPMENT_LEDGER.md`](../../DEVELOPMENT_LEDGER.md) and
[`ROADMAP.md`](../../ROADMAP.md).

The archive covers:

- the initial Port migration, phase qualifications, Early Window work, and SPI loading;
- paired Radiance/MCVR static audits, compatibility decisions, and diagnostic cleanup;
- the native descriptor/pipeline-layout crash investigation and its A/B experiments;
- Flywheel, Sable, Veil, framebuffer, public draw, and world-mesh compatibility work;
- Ponder PT, denoising, RR model, SHARC, and capture investigations;
- chunk correctness/performance experiments and the Sodium-inspired no-cull study;
- DLSS/FG/Reflex/Streamline upgrade assessments and F3/UI follow-ups;
- the former scratch checkout's Port coordination, mapping, readiness, and validation documents.

Repository-history recovery material was not added to Git. The 2026-09-19 develop-only cleanup and
2026-09-20 pre-amend bundles plus metadata are retained at
`D:\Workspaces\Backups\RadiancePortCore_20260921\RepositoryHistory`.

