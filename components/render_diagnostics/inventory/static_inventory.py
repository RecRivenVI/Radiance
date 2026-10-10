"""Repeatable static research inventories; no builds, downloads, client or performance runs.

Raw file bytes are hashed (no EOL normalization). Regex rows are explicitly leads,
not resolved callsites, ownership proof, or semantic equivalence verdicts. Exact
bytecode invocations are handled separately by DrawInventory.java.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import csv
import difflib
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import yaml

SUFFIXES = {".java", ".cpp", ".hpp", ".h", ".glsl", ".rgen", ".rchit", ".rahit", ".rmiss",
            ".comp", ".vert", ".frag", ".geom", ".json", ".gradle", ".cmake", ".properties", ".toml", ".yaml"}
STAGES = {".rgen", ".rchit", ".rahit", ".rmiss", ".comp", ".vert", ".frag", ".geom"}

def digest(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args]).decode("utf-8", errors="strict").strip()

def write(out: Path, name: str, data) -> None:
    (out / (name + ".json")).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if isinstance(data, list) and data and isinstance(data[0], dict):
        columns = list(dict.fromkeys(k for row in data for k in row))
        with (out / (name + ".csv")).open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=columns)
            w.writeheader()
            w.writerows({k: json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else v
                         for k, v in row.items()} for row in data)

def sources(root: Path, label: str) -> tuple[list[dict], dict[str, str]]:
    tracked = git(root, "ls-files", "-z").split("\0")
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z").split("\0")
    records, texts = [], {}
    for rel in sorted(set(tracked + untracked)):
        p = root / rel
        if not rel or not p.is_file() or rel.startswith(("third_party/", "components/native_renderer/third_party/", "components/configuration/", "components/conventions/", "components/compliance/", "documents/", "components/render_diagnostics/inventory/")):
            continue
        if p.suffix.lower() not in SUFFIXES and p.name not in {"CMakeLists.txt", ".gitmodules", ".gitignore", "LICENSE", "NOTICE"}:
            continue
        b = p.read_bytes()
        key = label + "/" + rel
        records.append(dict(repo=label, path=rel, bytes=len(b), sha256=digest(b),
                            tracked=rel in tracked, lines=len(b.splitlines())))
        texts[key] = b.decode("utf-8-sig", errors="replace")
    return records, texts

def matches(texts: dict[str, str], pattern: str) -> list[dict]:
    rx = re.compile(pattern)
    return [dict(path=p, line=i, text=s.strip(), evidence="static_lexical_lead")
            for p, text in sorted(texts.items()) for i, s in enumerate(text.splitlines(), 1)
            if rx.search(s) and not s.lstrip().startswith(("//", "*", "# "))]

def contextual_consumers(texts: dict[str, str], name: str, exclude: str) -> list[dict]:
    rx = re.compile(r"\b" + re.escape(name) + r"\b")
    return [dict(path=p, line=i, text=s.strip()) for p, text in sorted(texts.items()) if p != exclude
            for i, s in enumerate(text.splitlines(), 1) if rx.search(s) and not s.lstrip().startswith("//")]

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--radiance", type=Path, required=True)
    ap.add_argument("--mcvr", type=Path, required=True)
    ap.add_argument("--references", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    out = args.output.resolve()
    if out.exists():
        raise ValueError("Use a new output directory; evidence is immutable")
    out.mkdir(parents=True)
    pins, texts, repos = [], {}, {}
    for label, root in (("Radiance", args.radiance.resolve()), ("MCVR", args.mcvr.resolve())):
        rows, part = sources(root, label)
        pins.extend(rows); texts.update(part)
        repos[label] = dict(path=str(root), head=git(root, "rev-parse", "HEAD"),
                           parent=git(root, "rev-parse", "HEAD^"), tree=git(root, "rev-parse", "HEAD^{tree}"),
                           status=git(root, "status", "--porcelain=v1"),
                           index_diff=git(root, "diff", "--cached", "--name-only"))
    write(out, "source-files", pins)
    write(out, "source-snapshot", {"observed_on": datetime.now().astimezone().isoformat(), "repos": repos,
         "normalization": "none; SHA256 of raw file bytes; ordered paths use forward slashes",
         "files_manifest_sha256": digest((out / "source-files.json").read_bytes()),
         "limits": "First-party tracked/nonignored input only; dependency identities use reference manifest and SDK pins"})
    source_calls = []
    api = [ ("raw_gl", r"\b(?:[A-Z][A-Za-z0-9_]*\.)?\b(?:n?gl[A-Z]\w*)\s*\("),
        ("gl_state", r"\bGlStateManager\.\w+\s*\("),
        ("render_system", r"\bRenderSystem\.\w+\s*\("),
        ("render_state", r"\b(?:RenderType|RenderStateShard)\.\w+|\b(?:setShaderState|setTextureState|setTransparencyState|setCullState|setDepthTestState|setWriteMaskState)\s*\("),
        ("vertex_or_buffer_source", r"\b(?:addVertex|setUv|setColor|setNormal|setLight|setOverlay|getBuffer|endBatch|drawWithShader|drawWithUploader)\s*\("),
        ("gui", r"\b(?:guiGraphics|graphics|context)\.(?:blit\w*|fill\w*|draw\w*|render\w*|enableScissor|disableScissor)\s*\("),
        ("shader_or_post", r"\b(?:ShaderInstance|ShaderProgram|PostChain|PostPass|ShaderManager|PostProcessingManager|PostPipeline|FramebufferManager)\b"),
        ("target", r"\b(?:RenderTarget|AdvancedFbo|Framebuffer|FrameBuffer)\b|\b(?:bindRead|bindWrite|unbindRead|unbindWrite|blitToScreen)\s*\(")]
    ref_manifest = json.loads(args.references.read_text(encoding="utf-8"))
    for artifact in ref_manifest["artifacts"]:
        root = Path(artifact["source_root"])
        if not root.exists():
            continue
        for p in sorted(root.rglob("*.java")):
            text = p.read_text(encoding="utf-8-sig", errors="replace")
            for line, s in enumerate(text.splitlines(), 1):
                if s.lstrip().startswith(("//", "*", "import ", "package ")):
                    continue
                for layer, pattern in api:
                    for match in re.finditer(pattern, s):
                        key = "\n".join((artifact["project"], artifact["version"], str(p), str(line), str(match.start()), layer))
                        source_calls.append(dict(id=digest(key.encode()), project=artifact["project"],
                            version=artifact["version"], source=str(p), line=line, column=match.start()+1,
                            layer=layer, expression=match.group(), context=s.strip(),
                            original_semantics="Contextual drawing/state lead; arguments and caller require review",
                            intent="unknown", translation="unknown", radiance_evidence="",
                            runtime="static_only", confidence="lexical_candidate_not_resolved_invocation"))
    write(out, "source-draw-candidates", source_calls)
    raw_hashes={r["repo"]+"/"+r["path"]:r["sha256"] for r in pins}
    shaders = []
    for p, text in texts.items():
        if Path(p).suffix not in STAGES and not (p.startswith("MCVR/src/shader/") and p.endswith(".glsl")):
            continue
        lines = text.splitlines()
        shaders.append(dict(path=p, stage=Path(p).suffix, sha256=raw_hashes[p], lines=len(lines),
            includes=re.findall(r'^\s*#include\s+[<"]([^>"]+)', text, re.M),
            anyhit_predicates=[dict(line=i, text=s.strip()) for i,s in enumerate(lines,1)
                if Path(p).suffix==".rahit" and re.search(r'if\s*\(|ignoreIntersection|terminateRay|accepts|rayInside|rayIgnore',s)],
            special_branches=[dict(line=i,text=s.strip()) for i,s in enumerate(lines,1)
                if re.search(r'\b(?:ray[A-Z]\w*|acceptsUiOwner|acceptsWorldMaterialFace|isFirstPerson\w*|geometryFlags)\b',s)]))
    write(out,"shaders",shaders)
    pairs=[]
    items=[(s,texts[s["path"]].splitlines()) for s in shaders if s["stage"] in STAGES]
    for n,(a,al) in enumerate(items):
        if len(al)<15: continue
        for b,bl in items[n+1:]:
            if a["stage"]!=b["stage"] or abs(len(al)-len(bl))>max(10,len(al)//12): continue
            sm=difflib.SequenceMatcher(None,al,bl,autojunk=False)
            if sm.quick_ratio()<.96: continue
            ratio=sm.ratio()
            if ratio<.96: continue
            changes=[dict(kind=tag,a_start=i+1,a_end=j,b_start=k+1,b_end=l,
                          a_lines=al[i:j],b_lines=bl[k:l]) for tag,i,j,k,l in sm.get_opcodes() if tag!="equal"]
            pairs.append(dict(a=a["path"],b=b["path"],line_similarity=ratio,
                removed_lines=sum(x["a_end"]-x["a_start"]+1 for x in changes),
                added_lines=sum(x["b_end"]-x["b_start"]+1 for x in changes),changes=changes))
    write(out,"shader-near-duplicates",pairs)
    write(out,"bit-and-state-leads",matches(texts,r'\b(?:const|constexpr|static final|enum)\b.*(?:BIT|Bit|FLAG|Flag|MASK|Mask|SHIFT|Shift)|\b(?:flags|packedData|geometryTypeID|vertexFlags|instanceFlags)\b.*(?:<<|>>|0x)|1[uU]?\s*<<\s*\d'))
    write(out,"topology-leads",matches(texts,r'reconcileAuthored|attachPrimitiveFlags|authoredQuads|index_patterns|QUADS|TRIANGLE_STRIP|TRIANGLE_FAN|MINECRAFT_LINES|prepareDrawIndex|buildIndices|normalOffset'))
    write(out,"ownership-declarations",matches(texts,r'\b(?:std::(?:unordered_map|map|vector|deque|set)|Map<|List<|Set<|Queue<|AtomicLong|AtomicInteger|ConcurrentHashMap|HashMap|shared_ptr|unique_ptr)'))
    write(out,"ownership-events",matches(texts,r'\b(?:clear|erase|remove|reset|close|release\w*|destroy\w*|retire\w*|trim\w*|collect\w*|evict\w*|reserve|resize|allocate\w*)\s*\('))
    write(out,"specialization-leads",matches(texts,r'(?:instanceof|Class\.forName|getClass\(\)|getName\(\)|getSimpleName\(\)|contains\(|endsWith\(|equals\(|startsWith\().*(?:spring|Spring|Boat|boat|Player|Slime|Glow|Water|water|cloud|Cloud|Flywheel|Simulated|Sable|Aeronautics|Ponder|entity\.|textures/|minecraft:|veil:)|\binstanceof\s+\w+'))
    configs=[]
    yaml_configs=[]
    for p,text in texts.items():
        if p.endswith(".yaml") and "/resources/modules/" in p:
            obj=yaml.safe_load(text)
            if not isinstance(obj,dict):
                continue
            for attribute in obj.get("attributeConfigs",[]):
                name=attribute.get("name","")
                # Native modules often consume the short key; keep both as leads.
                keys=list(dict.fromkeys((name,name.rsplit(".",1)[-1])))
                yaml_configs.append(dict(path=p,name=name,type=attribute.get("type"),
                    default=attribute.get("value"),
                    declaration_line=next((i for i,s in enumerate(text.splitlines(),1)
                                           if name in s),None),
                    consumers=[row for k in keys if k for row in contextual_consumers(texts,k,p)],
                    meaning="YAML module declaration; short-key consumers are leads, not a proved settings mapping"))
            write(out,"graph-"+digest(p.encode())[:12],dict(path=p,graph=obj,format="module_yaml"))
            continue
        if not p.endswith(".json"): continue
        try: obj=json.loads(text)
        except json.JSONDecodeError: continue
        if not isinstance(obj,dict): continue
        for attribute in obj.get("attributes",[]):
            define=attribute.get("define")
            keys=[define] if isinstance(define,str) else list(define or {})
            configs.append(dict(path=p,name=attribute.get("name"),type=attribute.get("type"),
                default=attribute.get("default_value"),define=define,
                declaration_line=next((i for i,s in enumerate(text.splitlines(),1)
                                       if json.dumps(attribute.get("name")) in s),None),
                consumers=[row for k in keys for row in contextual_consumers(texts,k,p)],
                meaning="Meaning follows the attached define consumer; ranges/enums are source declarations, not live settings"))
        if any(k in obj for k in ("passes", "resources", "ray_tracing_pipeline", "sharc", "attributes")):
            write(out,"graph-"+digest(p.encode())[:12],dict(path=p,graph=obj))
    write(out,"module-attributes",configs)
    write(out,"module-yaml-attributes",yaml_configs)
    options=[]
    for p,text in texts.items():
        if not p.endswith(("Options.java","options.hpp","core/render/renderer.hpp")):continue
        for i,s in enumerate(text.splitlines(),1):
            m=re.search(r'(?:public static (?:boolean|int|float|double)|(?:bool|uint32_t|int32_t|float|int))\s+(\w+)\s*=\s*([^;]+);',s)
            if m:
                options.append(dict(path=p,line=i,name=m[1],default=m[2],
                    consumers=contextual_consumers(texts,m[1],p),meaning="See declaration and consumers; configured != live/effective"))
    write(out,"options",options)
    write(out,"configuration-ui",matches(texts,r'extends (?:Screen|AbstractContainerScreen)|new (?:ShaderPackScreen|ShaderPackSettingsScreen|RenderPipelineScreen|RenderFeaturesScreen|ModuleAttributeScreen)|shader_pack|ShaderPack|PIPELINE_SETUP|SHADER_PACK_SETUP'))
    write(out,"build-graph",matches(texts,r'tasks\.(?:register|named)|add_(?:library|executable|subdirectory|test)|install\(|generateJni|generate.*Header|prepareRuntime|distributed|jarJar|release|publish|verify.*Distribution|FetchContent_Declare'))
    sdks=[]
    for p in sorted((args.mcvr/"extern").iterdir()):
        if not p.is_dir():continue
        try: head=git(p,"rev-parse","HEAD"); description=git(p,"describe","--tags","--always")
        except subprocess.CalledProcessError: head=description=None
        license_files=[dict(path=str(x),sha256=digest(x.read_bytes())) for x in sorted(p.glob("*"))
                       if x.is_file() and re.search(r'license|copying|notice',x.name,re.I)]
        sdks.append(dict(name=p.name,head=head,description=description,license_files=license_files))
    write(out,"sdk-pins",sdks)
    write(out,"counts",dict(source_files=len(pins),source_draw_candidates=len(source_calls),
         layers=dict(Counter(r["layer"] for r in source_calls)),shaders=len(shaders),
         shader_stages=dict(Counter(s["stage"] for s in shaders)),near_duplicate_pairs=len(pairs),
         module_attributes=len(configs),module_yaml_attributes=len(yaml_configs),options=len(options),
         yaml_parser_version=yaml.__version__))
    print((out/"counts.json").read_text(encoding="utf-8"))

if __name__=="__main__":main()
