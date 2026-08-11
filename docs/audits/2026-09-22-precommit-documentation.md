# Documentation pre-commit review: 2026-09-22

Status: completed; documentation is ready for staging, but no commit was created by this review.
Evidence: static inspection, current Git/ref checks, active-document structure and link checks, and
full imported-archive hash verification. No product build, client run, GPU test, deployment, or
visual acceptance was performed.

## Scope

The review covered the complete intended documentation batch in Radiance and MCVR:

- upstream-authored root README/build notes, preserving their structure and subject range while
  correcting stale branch facts in place;
- both documentation indexes and implementation ledgers;
- the Radiance policy, roadmap, research, and dated audits;
- all six Codex task extractions and their archive index;
- the imported Port collection through its index, manifest, and every archived file hash;
- legal, vendor, runtime-license, and third-party notice paths as protected change boundaries.

The 194 imported Port records were not editorially normalized or reinterpreted. They are frozen
evidence, so this review verified their manifest identity instead of rewriting their prose or old
machine-local links.

## Findings and corrections

### Root README boundary

The upstream installation and TODO bodies described a missing-DLSS crash, dummy DLL files, DLSS
310.5.3, and frame generation as future work. The current Windows packaged source instead contains
pinned DLSS/Streamline runtimes and exposes DLSS SR, RR, 2x FG, global model selection, and Reflex
settings.

The affected English and Chinese sections now state the current installation target, packaged
runtime behavior, build entry points, and frame-generation status directly. Repository links were
also moved to the maintained forks. The upstream section layout, topic range, and general tone were
retained; no fork disclaimer or unrelated version/product scope was added.

### Authority and status boundaries

- The policy now requires stale facts in upstream-authored root documents to be corrected in place
  while preserving the original structure, tone, and subject range.
- Every active ledger and roadmap H2 entry has nearby `Status:` and `Evidence:` fields.
- Research files identify their observation date, pinned source baseline, and proposal/static
  boundary.
- The five newly archived tasks link to canonical ledger, roadmap, research, and retained evidence
  instead of promoting intermediate chat conclusions into current behavior.
- The crash synthesis keeps Java/Mixin startup failures, NVIDIA-driver CPU access violations,
  Vulkan device loss, native application failures, and launcher/audio failures distinct.
- MCVR's old “before committing” instruction is explicitly labeled as a superseded historical
  handoff, and its NRD proposal no longer reads like an unqualified current correction.

### Navigation and formatting

- Raw roadmap research paths were converted into links.
- Maintained Markdown files have one H1 and no heading-level jumps. Upstream root README heading
  style remains untouched.
- Repository-relative paths and heading fragments resolve within both repositories.
- Necessary absolute paths in active documents are labeled machine-local and non-portable.
- No mass whitespace cleanup was applied to upstream or imported documents. Unrelated inherited
  whitespace remains outside the diff; every changed or new line passes the whitespace gate.

## Historical archive integrity

`docs/history/port-artifacts-20260921/MANIFEST.csv` lists 194 records. Every archive-relative path
exists, every byte count and SHA-256 matches, and no unmanifested payload exists below the archive
root apart from its `README.md` and `MANIFEST.csv`.

The Codex archive index contains six extracted tasks. The five tasks added in the latest extraction
batch were completely paginated before archival; their task records report 47 pages and 441 turns.
Task archival is reversible and does not establish engineering acceptance.

## Remaining documented limitation

The root README remains bounded to the subjects already covered by the upstream document and facts
supported by the current source. It is not an exhaustive compatibility matrix or release promise;
adding new version lines, platform coverage, or product scope requires separate evidence. This does
not block committing the engineering-document batch.

## Validation result

- Radiance `develop` remained at and matched `origin/develop` commit
  `f9dd73bb0ab3463d952e0c720db06ac4010f36ce` before the documentation commit.
- MCVR `develop` remained at and matched `origin/develop` commit
  `5150670796380bf128fecec551c864f480bb05f9` before the documentation commit.
- All pending paths are documentation, historical records, or root README corrections; product
  source and build files are unchanged.
- Root English and Chinese README changes make paired factual corrections. Legal/vendor files have
  no diff.
- Active local links and anchors resolve, metadata/heading checks pass, imported hashes match, and
  both repositories pass `git diff --check`.

The batch remains intentionally unstaged and uncommitted for final user review.
