"""Offline, artifact-pinned reference acquisition. Never downloads or launches a client.

Copies cached archives, extracts text safely, and decompiles installed mod bytecode
with a caller-selected LOCAL Vineflower. Generated third-party sources stay outside
the repository. Existing manifests are immutable; use another output directory to rerun.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import zipfile


TEXT = {".java", ".json", ".toml", ".glsl", ".vsh", ".fsh", ".vert", ".frag",
        ".geom", ".comp", ".txt", ".md", ".properties", ".xml", ".cfg", ".mcmeta"}


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def extract_text(archive: Path, dest: Path) -> list[dict]:
    dest.mkdir(parents=True, exist_ok=True)
    result = []
    with zipfile.ZipFile(archive) as z:
        for entry in sorted(z.infolist(), key=lambda x: x.filename):
            p = PurePosixPath(entry.filename)
            if entry.is_dir() or p.suffix.lower() not in TEXT:
                continue
            if "\\" in entry.filename or p.is_absolute() or ".." in p.parts or any(":" in x for x in p.parts):
                raise ValueError(f"Unsafe ZIP member: {entry.filename}")
            target = dest.joinpath(*p.parts)
            data = z.read(entry)
            if target.exists() and target.read_bytes() != data:
                raise ValueError(f"Refusing to overwrite differing output: {target}")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            result.append({"entry": entry.filename, "bytes": len(data),
                           "sha256": hashlib.sha256(data).hexdigest()})
    return result


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--radiance", type=Path, required=True)
    ap.add_argument("--gradle-cache", type=Path, required=True)
    ap.add_argument("--prism-instance", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--java", type=Path, required=True)
    ap.add_argument("--vineflower", type=Path, required=True)
    ap.add_argument("--historical-root", type=Path)
    args = ap.parse_args()
    out = args.output.resolve()
    repo = args.radiance.resolve()
    if out == repo or repo in out.parents:
        raise ValueError("Third-party reference outputs must be outside Radiance")
    out.mkdir(parents=True, exist_ok=True)
    if (out / "MANIFEST.json").exists():
        raise ValueError("Output already has a completed manifest; choose a new directory")
    props = dict(line.split("=", 1) for line in (repo / "gradle.properties").read_text(
        encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
    mc, neo = props["minecraft_version"], props["neo_loader_version"]
    gradle = args.gradle_cache.resolve()
    mods = args.prism_instance / "minecraft/mods"
    version_tuple = "_".join(props[k] for k in
                            ("create_version", "sable_version", "aeronautics_version", "veil_version"))
    compat = repo / "build/radiance-compatibility-compile" / version_tuple
    cache_files = gradle / "modules-2/files-2.1"
    neo_cache = cache_files / "net.neoforged/neoforge" / neo
    candidates = []

    def add(project: str, version: str, binary: Path, source: Path | None,
            origin: str, kind: str, parent: str | None = None) -> None:
        if not binary.is_file():
            raise FileNotFoundError(binary)
        candidates.append(dict(project=project, version=version, binary=binary, source=source,
                               origin=origin, kind=kind, parent=parent))

    mc_sources = repo / f"build/moddev/artifacts/neoforge-{neo}-sources.jar"
    add("minecraft", mc, repo / f"build/moddev/artifacts/neoforge-{neo}-merged.jar",
        mc_sources, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
        f"Mojang-named NeoForm source with NeoForge {neo} patches; not pristine Mojang source")
    add("neoforge", neo, next(neo_cache.rglob(f"neoforge-{neo}-universal.jar")),
        next(neo_cache.rglob(f"neoforge-{neo}-sources.jar")),
        f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/", "publisher sources")
    add("create", props["create_version"], mods / "create-1.21.1-6.0.10.jar", None,
        "https://modrinth.com/mod/create/versions?g=1.21.1&l=neoforge", "installed bytecode")
    add("aeronautics-bundle", props["aeronautics_version"],
        mods / "create-aeronautics-bundled-1.21.1-1.3.2.jar", None,
        "https://modrinth.com/mod/create-aeronautics/versions?g=1.21.1&l=neoforge", "installed parent bundle")
    add("sable", props["sable_version"], mods / "sable-neoforge-1.21.1-2.0.5.jar", None,
        "https://modrinth.com/mod/sable/versions?g=1.21.1&l=neoforge", "installed bytecode")
    # Transitive embedded mods use their parent bundle as version/distribution authority.
    nested = [("aeronautics", "dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.2.jar", "1.3.2", "aeronautics-bundle"),
              ("simulated", "dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar", "1.3.2", "aeronautics-bundle"),
              ("flywheel", "flywheel-neoforge-1.21.1-1.0.6.jar", "1.0.6", "create"),
              ("veil", "veil-neoforge-1.21.1-4.3.2.jar", props["veil_version"], "sable"),
              ("ponder", "ponder-neoforge-1.0.82+mc1.21.1.jar", "1.0.82", "create"),
              ("sable-rapier", "dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.5.jar", "2.0.5", "sable")]
    for project, name, version, parent in nested:
        add(project, version, compat / name, None, "embedded-in:" + parent, "extracted compile dependency", parent)

    manifest = {"schema": 1, "observed_on": "2026-10-03", "downloads": [],
                "java": str(args.java), "vineflower": str(args.vineflower),
                "vineflower_sha256": sha(args.vineflower),
                "vineflower_version": "1.12.0", "artifacts": [], "auxiliary": [],
                "limits": ["Decompiler line numbers are generated source positions, not original bytecode lines.",
                           "Decompiler output is not a proof of source-level pixel equivalence.",
                           "Minecraft sources are patched NeoForm inputs, not pristine source."]}
    for p in [gradle / f"neoformruntime/artifacts/minecraft_{mc}_client.jar",
              gradle / f"neoformruntime/artifacts/minecraft_{mc}_client_mappings.txt",
              repo / f"build/moddev/artifacts/neoforge-{neo}.jar",
              repo / f"build/moddev/artifacts/neoforge-{neo}-client-extra-aka-minecraft-resources.jar",
              args.prism_instance / "mmc-pack.json"]:
        target = out / "inputs" / p.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, target)
        manifest["auxiliary"].append({"input": str(p), "retained": str(target),
                                      "bytes": p.stat().st_size, "sha256": sha(p)})
    historical_names = {"create": "create-6.0.10", "aeronautics-bundle": "aeronautics_bundled-1.3.2",
                        "aeronautics": "aeronautics_bundled-1.3.2", "simulated": "aeronautics_bundled-1.3.2",
                        "sable": "sable-2.0.5", "sable-rapier": "sable-2.0.5",
                        "flywheel": "flywheel-1.0.6-neoforge", "veil": "veil-4.3.2",
                        "ponder": "ponder-1.0.82+mc1.21.1"}
    historical_verified = {}
    for c in candidates:
        slug = c["project"]
        dest = out / slug
        dest.mkdir(parents=True, exist_ok=True)
        retained = dest / c["binary"].name
        shutil.copy2(c["binary"], retained)
        record = {k: str(v) if isinstance(v, Path) else v for k, v in c.items()}
        record.update(retained=str(retained), bytes=retained.stat().st_size, sha256=sha(retained))
        if c["parent"]:
            parent = next(p["binary"] for p in candidates if p["project"] == c["parent"])
            with zipfile.ZipFile(parent) as z:
                matches = [n for n in z.namelist() if n.endswith("/" + c["binary"].name)]
                # Some bundle entries have another filename but identical identity.
                if not matches:
                    matches = [n for n in z.namelist() if n.endswith(".jar") and
                               hashlib.sha256(z.read(n)).hexdigest() == record["sha256"]]
                if not matches:
                    raise ValueError(f"Not embedded in pinned parent: {slug}")
                raw = z.read(matches[0])
                if hashlib.sha256(raw).hexdigest() != record["sha256"]:
                    raise ValueError(f"Stale extracted dependency: {slug}")
                record["parent_entry"] = matches[0]
        record["asset_index"] = extract_text(retained, dest / "assets")
        historic = (args.historical_root / historical_names[slug]) if (
            args.historical_root and slug in historical_names) else None
        if historic and (historic / "REFERENCE.json").is_file():
            if str(historic) not in historical_verified:
                metadata = json.loads((historic / "REFERENCE.json").read_text(encoding="utf-8-sig"))
                source_index = []
                for p in sorted((historic / "src").rglob("*")):
                    if p.is_file():
                        source_index.append({"entry": p.relative_to(historic / "src").as_posix(),
                                             "bytes": p.stat().st_size, "sha256": sha(p)})
                source_index.sort(key=lambda i: i["entry"])
                tree = hashlib.sha256("".join(f'{i["sha256"]}  {i["entry"]}\n'
                                            for i in source_index).encode("utf-8")).hexdigest()
                declared = metadata.get("sourceTreeHash")
                if isinstance(declared, dict):
                    declared = declared.get("value")
                historical_verified[str(historic)] = {"metadata": metadata, "source_index": source_index,
                    "actual_tree_sha256": tree, "matches_declared_tree": tree == declared,
                    "metadata_sha256": sha(historic / "REFERENCE.json")}
            h = historical_verified[str(historic)]
            if not h["matches_declared_tree"]:
                raise ValueError(f"Historical source tree drift: {historic}; actual={h['actual_tree_sha256']}")
            record.update(historical_reference=str(historic), historical_verification=h,
                          source_sha256=h["actual_tree_sha256"], source_index=h["source_index"],
                          source_root=str(historic / "src"), decompiler="historical REFERENCE.json provenance",
                          source_correspondence="Pinned declared release source; not byte-for-byte equivalence to installed classes")
            if slug in {"aeronautics", "simulated"}:
                record["source_root"] = str(historic / "src" / slug)
            manifest.setdefault("historical_location_authority", "User explicitly supplied D:/Workspaces/References/minecraft-references on 2026-10-03")
        elif c["source"]:
            retained_source = dest / c["source"].name
            shutil.copy2(c["source"], retained_source)
            record["source_sha256"] = sha(retained_source)
            record["source_index"] = extract_text(retained_source, dest / "sources")
            record["decompiler"] = "cached publisher/NeoForm source archive; historical tool provenance not fully reconstructed"
        elif not any(n.endswith(".class") for n in zipfile.ZipFile(retained).namelist()):
            record.update(source_index=[], source_sha256=None, decompiler="Container only; inspect embedded mods",
                          source_root=str(dest / "sources"))
        else:
            decompiled = dest / "decompiled"
            decompiled.mkdir(exist_ok=True)
            cmd = [str(args.java), "-Xmx2G", "-jar", str(args.vineflower),
                   "-thr=2", "-log=WARN", str(retained), str(decompiled)]
            log = dest / "decompiler.log"
            # Vineflower 1.12 can emit a directory rather than a source JAR.
            # Resume only an output for this immutable copied input, not other cached versions.
            if log.exists() and any(decompiled.rglob("*.java")):
                code = 0
            else:
                with log.open("wb") as f:
                    code = subprocess.call(cmd, stdout=f, stderr=subprocess.STDOUT)
            record.update(decompiler="Vineflower 1.12.0", decompiler_command=cmd,
                          decompiler_exit=code, decompiler_log_sha256=sha(log))
            jars = sorted(decompiled.glob("*.jar"))
            if code or (not jars and not any(decompiled.rglob("*.java"))):
                raise RuntimeError(f"Decompiler failed for {slug}: exit={code}, archives={jars}")
            if jars:
                if len(jars) != 1:
                    raise RuntimeError(f"Ambiguous decompiler archives: {jars}")
                record["source_sha256"] = sha(jars[0])
                record["source_index"] = extract_text(jars[0], dest / "sources")
            else:
                record["source_index"] = []
                for p in sorted(decompiled.rglob("*")):
                    if p.is_file() and p.suffix.lower() in TEXT:
                        rel = p.relative_to(decompiled)
                        target = dest / "sources" / rel
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(p, target)
                        record["source_index"].append({"entry": rel.as_posix(),
                            "bytes": p.stat().st_size, "sha256": sha(p)})
                record["source_sha256"] = hashlib.sha256(json.dumps(
                    record["source_index"], sort_keys=True).encode("utf-8")).hexdigest()
        record.setdefault("source_root", str(dest / "sources"))
        record["java_files"] = sum(1 for _ in Path(record["source_root"]).rglob("*.java"))
        manifest["artifacts"].append(record)
        (out / "PROGRESS.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
        print(json.dumps({"project": slug, "java_files": record["java_files"],
                          "sha256": record["sha256"]}), flush=True)
    (out / "MANIFEST.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
