"""Finalize an offline inventory case; only analysis-tool tests and read-only Git checks.

Never builds/loads the product, stages files, changes refs, fetches or downloads.
Raw evidence hashes are reproducible; timestamps and current status are observations.
"""
from __future__ import annotations
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote
from static_inventory import sources


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def command(args: list[str], cwd: Path) -> dict:
    result = subprocess.run(args, cwd=cwd, capture_output=True, encoding="utf-8", errors="replace")
    return dict(command=args, returncode=result.returncode, stdout=result.stdout, stderr=result.stderr)


def git(root: Path, *args: str) -> str:
    result = command(["git", "-C", str(root), *args], root)
    if result["returncode"]:
        raise RuntimeError(result)
    return result["stdout"].strip()


def links(text: str) -> set[str]:
    return set(re.findall(r"\[[^\]\n]*\]\(([^)\n]+)\)", text))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--radiance", type=Path, required=True)
    parser.add_argument("--mcvr", type=Path, required=True)
    parser.add_argument("--case", type=Path, required=True)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--javac", type=Path, required=True)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--check-only", action="store_true", help="Report checks before creating final evidence files")
    args = parser.parse_args()
    root, native, case = args.radiance.resolve(), args.mcvr.resolve(), args.case.resolve()
    validation_path, evidence_path = case / "VALIDATION.json", case / "EVIDENCE_MANIFEST.json"
    if validation_path.exists() or evidence_path.exists():
        raise ValueError("Final evidence already exists; use a new case, never overwrite it")
    frozen = json.loads((case / "static-final/source-snapshot.json").read_text(encoding="utf-8"))
    records = json.loads((case / "static-final/source-files.json").read_text(encoding="utf-8"))
    earlier = json.loads((case / "static/source-files.json").read_text(encoding="utf-8"))
    live, repositories, failures = [], {}, []
    for label, repo in (("Radiance", root), ("MCVR", native)):
        rows, _ = sources(repo, label)
        live.extend(rows)
        observed = dict(head=git(repo, "rev-parse", "HEAD"),
                        parent=git(repo, "rev-parse", "HEAD^"),
                        tree=git(repo, "rev-parse", "HEAD^{tree}"),
                        status=git(repo, "status", "--porcelain=v1", "--untracked-files=all"),
                        staged=git(repo, "diff", "--cached", "--name-only"))
        observed["commits_above_parent"] = git(repo, "log", "--format=%s", observed["parent"] + "..HEAD").splitlines()
        observed["whitespace_check"] = command(["git", "-C", str(repo), "diff", "--check"], repo)
        changed = git(repo, "diff", "--name-only").splitlines()
        untracked = git(repo, "ls-files", "--others", "--exclude-standard").splitlines()
        outside = [p for p in changed + untracked if not
                   (p.startswith("docs/") or (label == "Radiance" and p.startswith("tools/inventory/")))]
        observed["unexpected_candidate_paths"] = outside
        if outside or observed["staged"] or observed["whitespace_check"]["returncode"]:
            failures.append(label + ": candidate/index/whitespace boundary")
        if any(observed[k] != frozen["repos"][label][k] for k in ("head", "parent", "tree")):
            failures.append(label + ": frozen Git identity changed")
        if observed["commits_above_parent"] != ["Initial port"]:
            failures.append(label + ": checkpoint structure changed")
        repositories[label] = observed
    if live != records or live != earlier:
        failures.append("First-party input bytes/range differ from the initial/final static snapshot")

    reports = sorted((root / "docs/relay").glob("2026-10-03-*-gpt-to-claude-a*.md"))
    reports += sorted((root / "docs/relay").glob("2026-10-04-*-gpt-to-claude-static-tasks-summary.md"))
    report_checks = []
    for path in reports:
        text = path.read_text(encoding="utf-8")
        summary = re.search(r"^## Summary\n(.*?)(?=^## |\Z)", text, re.M | re.S)
        length = len(summary.group(1).strip().splitlines()) if summary else 999
        header = all(re.search(r"^" + name + r": .+", text, re.M)
                     for name in ("From", "To", "Type", "Status", "Replies-to"))
        report_checks.append(dict(path=path.relative_to(root).as_posix(), summary_lines=length, header_complete=header))
        if length > 30 or not header:
            failures.append("Invalid report header/summary: " + str(path))
    if len(reports) != 9:
        failures.append("Expected eight task reports and one execution summary")
    link_checks = []
    for repo in (root, native):
        paths = set(git(repo, "diff", "--name-only").splitlines())
        paths.update(git(repo, "ls-files", "--others", "--exclude-standard").splitlines())
        for rel in sorted(p for p in paths if p.endswith(".md")):
            path = repo / rel
            if not path.is_file():
                continue
            current = links(path.read_text(encoding="utf-8"))
            previous = command(["git", "-C", str(repo), "show", "HEAD:" + rel], repo)
            if not previous["returncode"]:
                current -= links(previous["stdout"])
            for link in sorted(current):
                target = link.strip("<>").split("#", 1)[0]
                if not target or re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", target):
                    continue
                exists = (path.parent / unquote(target)).resolve().is_file()
                link_checks.append(dict(document=str(path), target=link, file_exists=exists,
                                        boundary="file target; Markdown anchors not certified"))
                if not exists:
                    failures.append("Broken added local link: " + str(path) + " -> " + link)

    java_classes = case / "classes"
    java_classes.mkdir(exist_ok=True)
    compile_check = command([str(args.javac), "-cp", args.classpath, "-d", str(java_classes),
                             "tools/inventory/DrawInventory.java", "tools/inventory/DrawInventoryTest.java"], root)
    tests = [compile_check, command([sys.executable, "-m", "unittest", "discover", "-s", "tools/inventory", "-p", "test_inventory.py", "-v"], root)]
    if not compile_check["returncode"]:
        tests.append(command([str(args.java), "-cp", str(java_classes) + ";" + args.classpath, "DrawInventoryTest"], root))
    if any(t["returncode"] for t in tests):
        failures.append("Analysis-tool fixture failed")
    validation = dict(completed_at=datetime.now().astimezone().isoformat(),
                      scope="offline analysis tools/docs only; no product/runtime/GPU/performance test",
                      repositories=repositories, first_party_file_count=len(live),
                      first_party_bytes_equal_initial_and_final=live == records == earlier,
                      reports=report_checks, added_local_links=link_checks, tool_checks=tests,
                      failures=failures)
    if args.check_only:
        print(json.dumps(dict(first_party_files=len(live), report_count=len(reports), local_links=len(link_checks),
                              tool_results=[dict(returncode=t["returncode"], stdout=t["stdout"], stderr=t["stderr"]) for t in tests],
                              failures=failures), ensure_ascii=False))
        raise SystemExit(1 if failures else 0)
    validation_path.write_text(json.dumps(validation, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    inventories = []
    for path in sorted(case.rglob("*")):
        if path.is_file() and path.suffix in {".json", ".csv"} and path != evidence_path:
            rel = path.relative_to(case).as_posix()
            disposition = "final" if rel.split("/")[0] in {"bytecode-v2", "draw-directory", "static-final", "supplement-complete", "VALIDATION.json"} else "provisional; retained, excluded from final findings"
            inventories.append(dict(path=rel, bytes=path.stat().st_size, sha256=sha(path), disposition=disposition))
    tool_files = [p for p in sorted((root / "tools/inventory").iterdir()) if p.is_file()]
    evidence = dict(completed_at=datetime.now().astimezone().isoformat(), normalization="none; SHA256 of raw bytes",
                    reference_manifest=dict(path=str(args.references.resolve()), sha256=sha(args.references)),
                    inventories=inventories,
                    analysis_sources=[dict(path=p.relative_to(root).as_posix(), sha256=sha(p)) for p in tool_files],
                    reports=[dict(path=p.relative_to(root).as_posix(), sha256=sha(p)) for p in reports],
                    scope=validation["scope"], validation_passed=not failures)
    evidence_path.write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(dict(validation=str(validation_path), manifest=str(evidence_path),
                          first_party_files=len(live), report_count=len(reports), local_links=len(link_checks),
                          hashed_inventories=len(inventories), failures=failures), ensure_ascii=False))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
