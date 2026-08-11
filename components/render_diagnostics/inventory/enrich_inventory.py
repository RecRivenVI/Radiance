"""Attach inspectable bridge leads without converting them to equivalence verdicts."""
from __future__ import annotations
import argparse, hashlib, json
from pathlib import Path
from static_inventory import write

ROUTES = {
    "raw_gl": "versions/1.21.1-neoforge/src/main/java/com/radiance/mixins/vulkan_render_integration/GlStateManagerMixins.java",
    "gl_state": "versions/1.21.1-neoforge/src/main/java/com/radiance/mixins/vulkan_render_integration/GlStateManagerMixins.java",
    "render_system": "versions/1.21.1-neoforge/src/main/java/com/radiance/mixins/vulkan_render_integration/RenderSystemMixins.java",
    "render_state": "versions/1.21.1-neoforge/src/main/java/com/radiance/client/render/MaterialFaces.java",
    "vertex_or_buffer_source": "versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/world/EntityProxy.java",
    "gui": "versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/vulkan/DrawCommandProxy.java",
    "target": "versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/vulkan/FramebufferProxy.java",
    "shader_or_post": "versions/1.21.1-neoforge/src/main/java/com/radiance/client/proxy/vulkan/ShaderProxy.java",
    "veil_shader_post_target": "versions/1.21.1-neoforge/src/main/java/com/radiance/mixins/compatibility/veil/VeilShaderProgramImplMixins.java",
}

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--radiance", type=Path, required=True)
    ap.add_argument("--references", type=Path, required=True)
    ap.add_argument("--bytecode", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    a=ap.parse_args()
    if a.output.exists(): raise ValueError("Choose a new evidence output directory")
    a.output.mkdir(parents=True)
    manifest=json.loads(a.references.read_text(encoding="utf-8"))
    identities={r["sha256"]:r for r in manifest["artifacts"]}
    sources={}
    for r in manifest["artifacts"]:
        root=Path(r["source_root"])
        for f in root.rglob("*.java"):
            sources.setdefault(f.name, []).append(str(f))
    rows=json.loads((a.bytecode/"draw-sites.json").read_text(encoding="utf-8"))
    for r in rows:
        identity=identities.get(r["artifact_sha256"])
        r["component"]=identity["project"] if identity else "transitive_unresolved"
        r["component_version"]=identity["version"] if identity else "see enclosing archive; not inferred from distribution version"
        rel=ROUTES[r["layer"]]; f=a.radiance/rel
        if not f.is_file(): raise FileNotFoundError(f)
        line=next((i for i,s in enumerate(f.read_text(encoding="utf-8-sig").splitlines(),1)
                   if "class " in s),1)
        r["radiance_evidence"]=f"Radiance/{rel}:{line}"
        r["evidence_scope"]="Related bridge to investigate, not proof this callsite is intercepted"
        basename=r["source"].split("/")[-1].split("$")[0].replace(".class", ".java")
        r["source_candidates"]=sorted(set(sources.get(basename, [])))
        r["notes"] += "; Bytecode source line must not be used as a decompiler output line"
    write(a.output,"draw-directory",rows)
    write(a.output,"review-scope",dict(rows=len(rows),translation_verdicts="UNKNOWN until contextual review",
        source_manifest_sha256=hashlib.sha256(a.references.read_bytes()).hexdigest(),
        bytecode_manifest_sha256=hashlib.sha256((a.bytecode/"draw-sites.json").read_bytes()).hexdigest(),
        bridge_files=ROUTES,limits="Bridge leads and source candidates preserve unknowns; no inferred pixel equivalence"))
    print(json.dumps({"rows":len(rows),"all_statuses":"UNKNOWN; curated report distinguishes known semantic changes"}))

if __name__=="__main__":main()
