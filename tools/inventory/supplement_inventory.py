"""Offline declaration, JNI-name, packaged-input and license inventories.

No target classes are loaded, no dependencies fetched, and no product build runs.
Declaration/regex matches are candidates, not ABI, ownership or runtime proofs.
"""
from pathlib import Path
import argparse, hashlib, json, re, subprocess
from static_inventory import write, sources

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--radiance',type=Path,required=True)
    p.add_argument('--mcvr',type=Path,required=True)
    p.add_argument('--references',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); out=a.output.resolve()
    if out.exists(): raise ValueError('Use a new immutable output directory')
    out.mkdir(parents=True)
    text={}
    for label,root in [('Radiance',a.radiance),('MCVR',a.mcvr)]:
        _,t=sources(root,label); text.update(t)
    refs=json.loads(a.references.read_text(encoding='utf-8'))
    states=[]
    for component in refs['artifacts']:
        for f in sorted(Path(component['source_root']).rglob('*.java')):
            lines=f.read_text(encoding='utf-8-sig',errors='replace').splitlines()
            for i,line in enumerate(lines,1):
                if re.search(r'(?:new\s+\w*(?:StateShard|RenderState)|set(?:Shader|Texture|Transparency|Cull|DepthTest|WriteMask|Layering|Output|Texturing|Lightmap|Overlay)State\s*\()',line):
                    states.append(dict(project=component['project'],version=component['version'],path=str(f),
                        line=i,text=line.strip(),following_context=lines[i:min(len(lines),i+8)],
                        evidence='declaration_lead',translation='unknown'))
    write(out,'render-state-definitions',states)
    declarations=[]; exports=[]; namespaces=[]
    for path,t in sorted(text.items()):
        for i,line in enumerate(t.splitlines(),1):
            if path.endswith('.java') and re.search(r'\bnative\b.*\(',line):
                declarations.append(dict(path=path,line=i,text=line.strip()))
            if path.endswith(('.cpp','.hpp','.h')) and re.search(r'\bJava_[A-Za-z0-9_]+',line):
                exports.append(dict(path=path,line=i,names=re.findall(r'\bJava_[A-Za-z0-9_]+',line),text=line.strip()))
            if re.search(r'(?:constexpr|const|static final).*?(?:<<|0x|BIT|MASK)|\benum\s+(?:class\s+)?\w+|\b(?:GL_[A-Z_]+|[A-Z_]+)\s*\([^\n]+,?\s*(?:0b[01]+|\d+)\)',line):
                namespaces.append(dict(path=path,line=i,text=line.strip(),evidence='namespace_declaration_lead'))
    write(out,'jni-java-declarations',declarations); write(out,'jni-export-name-leads',exports)
    write(out,'enum-and-bit-declarations',namespaces)
    license_files=[]; resources=[]; workflows=[]
    for label,root in [('Radiance',a.radiance.resolve()),('MCVR',a.mcvr.resolve())]:
        tracked=subprocess.check_output(['git','-C',str(root),'ls-files','-z']).decode().split('\0')
        for rel in tracked:
            f=root/rel
            if not f.is_file():continue
            b=f.read_bytes(); row=dict(repo=label,path=rel,bytes=len(b),sha256=hashlib.sha256(b).hexdigest())
            if re.search(r'license|notice|copying|third[-_]?party',f.name,re.I) or '/licenses/' in rel.lower():license_files.append(row)
            if rel.startswith(('src/main/resources/','src/bootstrap/resources/')) or (rel.startswith('Modules/') and '/src/main/resources/' in rel):resources.append(row)
            if rel.startswith('.github/'):workflows.append(row)
    # Cached DLL identities are build inputs, not evidence of a current deploy or license approval.
    dlls=[]
    roots=[a.radiance/'src/main/resources',a.mcvr/'extern',a.mcvr/'build-radiance-1.21.1-neoforge/_deps/streamline_sdk-src']
    for root in roots:
        if not root.exists():continue
        for f in sorted(root.rglob('*.dll')):
            b=f.read_bytes(); dlls.append(dict(path=str(f.resolve()),bytes=len(b),sha256=hashlib.sha256(b).hexdigest(),
                status='cached_or_install_input; not inferred loaded/distributable'))
    write(out,'first-party-notices',license_files);write(out,'tracked-resources',resources)
    write(out,'tracked-github-files',workflows);write(out,'cached-runtime-dlls',dlls)
    write(out,'counts',dict(render_state_leads=len(states),java_native_declarations=len(declarations),
        export_name_lines=len(exports),enum_bit_leads=len(namespaces),tracked_resources=len(resources),
        cached_dlls=len(dlls),limits='Regex inventories are supplemental; repeated roots and generated headers may duplicate rows'))
    print((out/'counts.json').read_text())

if __name__=='__main__':main()
