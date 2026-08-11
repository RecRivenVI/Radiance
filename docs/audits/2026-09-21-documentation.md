# Documentation audit: 2026-09-21

Status: archived as a dated audit; the first documentation-only remediation pass is applied in the
same uncommitted batch and recorded below.

Evidence level: static inspection of Markdown/text files and repository-relative links in the
Radiance and sibling MCVR worktrees. No product build or runtime test was needed.

Correction: the later
[`2026-09-22` pre-commit review](2026-09-22-precommit-documentation.md) supersedes this audit's
final link counts, its statement that the root README files had no diff, and the minimal-diff rule
described in its remediation section. The root README files now correct stale facts in place while
preserving the upstream structure, tone, and subject range. The original findings remain the dated
first-pass record.

## Scope

The audit reviewed:

- Radiance and MCVR root README/build notes;
- active `docs/README.md`, ledgers, roadmap, and research files;
- the Codex task-extraction index and first extraction;
- the imported Port history at the collection/index level;
- legal/vendor files only to classify them outside editorial cleanup.

The imported history was not re-reviewed line by line. It is intentionally a frozen evidence set.

## Findings

### P0: user-facing README claims are stale

Radiance's upstream-derived English and Chinese README files still claim that a missing DLSS runtime
causes a crash, advise AMD users to create dummy DLL files, pin download instructions to DLSS
`v310.5.3`, and describe frame generation as a future TODO. Those claims can conflict with the
current packaged runtime, Streamline integration, model selection, FSR/NRD paths, and implemented FG.

The branch banner is concise and correct as a build entry, but it does not neutralize contradictory
installation and capability claims lower in the document. The English and Chinese variants must be
reviewed together against the packaged runtime manifest and current option behavior before being
rewritten. Do not delete the legal notices or replace them with inferred licensing guidance.

### P1: dated dirty-worktree checkpoints are presented as current

Both ledgers contain a `2026-09-21 dirty-worktree checkpoint`. The repositories were later amended
into their single `Initial port` commits and pushed. MCVR still says "Before committing" and both
files describe uncommitted source as current. These entries remain valid historical snapshots, but
they need a later correction entry identifying the committed revisions and explaining which claims
remain current.

The correction must be appended; the historical checkpoint should not be silently rewritten.

### P1: Radiance ledger heading hierarchy obscures independent events

Workspace cleanup, external evidence migration, dirty-worktree organization, and the Windows
generator guard are independent dated events but are H3 sections beneath the dirty-worktree H2.
This makes them appear to be subcomponents of one checkpoint and makes stable linking harder. Future
entries must use H2. Existing entries should receive stable H2 headings in a bounded editorial pass
that does not alter their claims.

### P1: MCVR lacked a documentation entry point

MCVR had a ledger and two research files but no `docs/README.md`, leaving their authority and their
relationship to Radiance undiscoverable. This audit adds a small index and points to the single
cross-repository policy rather than copying it.

### P1: evidence language is careful but not normalized

The active ledgers generally distinguish build, runtime, deployment, and visual evidence well.
However, status phrases are free-form (`candidate implementation retained`, `structural refactor`,
`implemented and partially accepted`) and readers must interpret their strength. The new policy
defines a controlled vocabulary while allowing multiple statuses on one entry.

### P2: roadmap and research overlap without a uniform backlink contract

The current roadmap summarizes research responsibly and often links to the supporting file. The
relationship is inconsistent across entries, and MCVR-native research is referenced only through a
general sibling-directory statement. Each roadmap work package should link to its source research;
completed portions should link to the ledger entry that records them.

Research files also use phrases such as "current source" without always pinning a commit. Future
research must name the source revision or observation date so later code does not inherit a stale
finding.

### P2: active documents contain non-portable operational paths

Radiance's ledger contains absolute workspace, artifact, scratch, and backup paths. Some are valid
operational evidence, especially retained recovery bundles; others can be expressed as repository-
relative paths or scoped environment notes. The policy permits necessary absolute paths but requires
them to be labeled non-portable.

No broken repository-relative Markdown links were found in active documents during this audit.
Historical imported reports contain old absolute `D:\Workspaces\Artifacts` links by design; they
must not be mass-edited. The archive index already warns that raw sources may have been recycled.

### P2: no automated documentation gate exists

The current checks were manual: relative-link resolution, absolute-path scanning, heading review,
status-language review, and `git diff --check`. A later small repository script should automate the
stable subset for both repositories without trying to validate frozen historical links or external
web availability.

### P3: imported history is large but correctly isolated

The imported Port archive is much larger than the maintained active documents and contains
inconsistent headings, languages, paths, and status wording. This is acceptable because the archive
has a manifest and explicit historical boundary. Normalizing it would create large low-value diffs
and risk changing evidence. Maintain only its index, manifest integrity, and external corrections.

### P3: legal and vendor notices need a separate change boundary

License, notice, runtime manifest, and third-party attribution files are not normal prose cleanup
targets. They should be reviewed only when dependencies, distribution, or license obligations
change.

### P3: minor root README formatting debt exists

The existing English Radiance README contains five lines with trailing whitespace. They predate
this audit and do not justify a standalone formatting diff. Remove them when the stale README
content is rewritten, while keeping that edit separate from legal/vendor files.

## Existing strengths

- Radiance already separates ledger, roadmap, research, and imported history.
- The roadmap explicitly says that a recorded item is not implementation authorization.
- Research documents usually include an evidence-boundary section.
- The latest ledger entries distinguish artifact identity from launch and visual acceptance.
- Thread extraction records scope drift and superseded conclusions instead of copying transcripts.
- Active repository-relative Markdown links passed the audit.

## Ordered remediation backlog

1. Verify actual packaged DLSS/Streamline/FSR/NRD/FG behavior and rewrite both Radiance root README
   variants with semantic parity. This is the highest user-facing risk.
2. Append post-amend correction entries to both ledgers with the current commit IDs, clean worktree
   state, retained source scope, and evidence boundary.
3. Promote independent Radiance ledger events from H3 to H2 without changing their historical text.
4. Add explicit research and ledger backlinks to each roadmap work package.
5. Pin research observations to exact source revisions or dates where "current" is ambiguous.
6. Label necessary machine-local paths as non-portable and replace incidental paths with relative
   links.
7. Add a bounded documentation checker for active documents only: local links, heading shape,
   unexplained absolute paths, trailing whitespace, and root README parity markers.

Each remediation should be a reviewable documentation-only change. Do not combine it with renderer
implementation or use documentation cleanup to revise product decisions.

## Remediation applied in this batch

- Added the documentation policy and the Radiance/MCVR documentation indexes.
- Marked the old dirty-worktree entries as historical and appended post-amend corrections tied to
  Radiance `f9dd73bb0ab3463d952e0c720db06ac4010f36ce` and MCVR
  `5150670796380bf128fecec551c864f480bb05f9`.
- Promoted independent Radiance ledger events to stable H2 sections without changing their
  technical claims.
- Normalized roadmap status wording and added direct backlinks to supporting ledger, research, and
  historical-evidence records.
- Pinned maintained research records to an observation date and source baseline.
- Labeled retained absolute operational paths as machine-local and non-portable.

The root README findings remain intentionally deferred. Those files originated upstream, and the
current repository rule requires the smallest practical diff there. Their stale product claims
need a dedicated, evidence-backed edit rather than being folded into documentation normalization.
The automated documentation checker also remains proposed.

## Validation performed

- Enumerated maintained Markdown/text documents and their heading counts in both repositories.
- Checked 22 repository-relative links in active Radiance documents and found zero broken links.
- MCVR active documents contained no repository-relative links before its new index.
- Scanned active documents for absolute local paths and evidence/status terminology.
- Kept the imported history and legal/vendor content outside normal editorial cleanup.

Post-remediation validation in the same uncommitted batch:

- `git diff --check` passed in both repositories, and changed or new documentation contained no
  trailing whitespace.
- 31 active Radiance repository-relative links and 3 active MCVR repository-relative links
  resolved to existing files. Frozen imported Port history was excluded from this active-doc gate.
- Git status contained documentation paths only; Radiance and MCVR product source was unchanged.
- The upstream-derived root README, license, and Windows build-note files had no new diff.
