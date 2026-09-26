package com.radiance.audit.tools;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Artifact-scoped bytecode inventory. A reachable GL call is not a proof of runtime execution. */
public final class GlInventory {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public record Artifact(String path, String role) {}
    public record Source(String artifact, String sha256, String role, String entry) {}
    public record Site(String id, Source source, String owner, String method, String descriptor,
                       int instruction, int line, String target, String kind, String translation,
                       String semanticChange, String evidence) {}
    public record Edge(String caller, String callee, String invocation) {}
    public record Hook(Source source, String mixin, String method, String annotation, String values) {}
    public record Scan(List<Site> calls, List<Edge> edges, List<Hook> hooks, List<String> risks,
                       Map<String,Integer> classDefinitions, int classes, int methods) {}
    public record Decision(String id, String translation, String semanticChange, String evidence) {}
    /** Decisions bind to an artifact-specific call site. Stale or unsupported claims fail closed. */
    public static Scan review(Scan scan, List<Decision> decisions) {
        Map<String, Decision> remaining = new HashMap<>();
        for (var d : decisions) {
            if (!Set.of("UNKNOWN", "TRANSLATED", "REPLACED", "INTENTIONALLY_SKIPPED", "UNSUPPORTED", "VANILLA_RETAINED").contains(d.translation())
                || !Set.of("UNKNOWN", "PRESERVED", "INTENTIONAL_CHANGE", "DEFECT").contains(d.semanticChange()))
                throw new IllegalArgumentException("Invalid decision: " + d.id());
            if (d.evidence() == null || d.evidence().isBlank())
                throw new IllegalArgumentException("Reviewed decisions require evidence: " + d.id());
            if (remaining.put(d.id(), d) != null) throw new IllegalArgumentException("Duplicate decision: " + d.id());
        }
        List<Site> calls = new ArrayList<>();
        for (var s : scan.calls()) {
            var d = remaining.remove(s.id());
            calls.add(d == null ? s : new Site(s.id(), s.source(), s.owner(), s.method(), s.descriptor(),
                s.instruction(), s.line(), s.target(), s.kind(), d.translation(), d.semanticChange(), d.evidence()));
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Stale/out-of-scope decisions: " + remaining.keySet());
        return new Scan(calls, scan.edges(), scan.hooks(), scan.risks(), scan.classDefinitions(), scan.classes(), scan.methods());
    }
    private static String digest(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String method(String owner, String name, String desc) { return owner+"#"+name+desc; }
    private static boolean gl(String owner, String name) {
        return owner.startsWith("org/lwjgl/opengl/") && (name.startsWith("gl") || name.startsWith("ngl"));
    }
    private static String kind(String name) {
        name=name.replaceFirst("^n(?=gl)", "");
        if (name.matches("gl(Multi)?Draw.*|gl(Begin|End|CallList.*|Rect.*)")) return "DRAW";
        if (name.startsWith("glBlit") || name.startsWith("glCopy")) return "COPY_OR_BLIT";
        if (name.startsWith("glClear")) return "CLEAR";
        if (name.startsWith("glDispatch")) return "COMPUTE";
        return "STATE_OR_RESOURCE";
    }
    public static Scan scan(List<Artifact> artifacts) throws IOException {
        List<Site> calls=new ArrayList<>(); List<Edge> edges=new ArrayList<>();
        List<Hook> hooks=new ArrayList<>(); List<String> risks=new ArrayList<>();
        Map<String,Integer> definitions=new TreeMap<>(); int[] counts=new int[2];
        for (Artifact a:artifacts) {
            Path p=Path.of(a.path()).toAbsolutePath().normalize();
            byte[] bytes=Files.readAllBytes(p);
            archive(bytes,p.toString(),digest(bytes),a.role(),calls,edges,hooks,risks,definitions,counts,0);
        }
        return new Scan(calls,edges,hooks,risks,definitions,counts[0],counts[1]);
    }
    private static void archive(byte[] bytes,String artifact,String hash,String role,List<Site> calls,
            List<Edge> edges,List<Hook> hooks,List<String> risks,Map<String,Integer> definitions,
            int[] counts,int depth) throws IOException {
        if(depth>8) throw new IOException("Nested JAR depth exceeds 8: "+artifact);
        if(bytes.length<4 || bytes[0]!='P' || bytes[1]!='K')throw new IOException("Not a ZIP/JAR: "+artifact);
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(ZipEntry entry; (entry=zip.getNextEntry())!=null;) {
                if(entry.isDirectory())continue;
                String name=entry.getName();
                if(name.endsWith(".jar")) {
                    byte[] nested=zip.readNBytes(256*1024*1024+1);
                    if(nested.length>256*1024*1024)throw new IOException("Nested JAR too large: "+name);
                    archive(nested,artifact+"!"+name,digest(nested),role,calls,edges,hooks,risks,definitions,counts,depth+1);
                } else if(name.endsWith(".class")) {
                    byte[] b=zip.readNBytes(16*1024*1024+1);
                    if(b.length>16*1024*1024)throw new IOException("Class too large: "+name);
                    Source source=new Source(artifact,hash,role,name);
                    inspect(b,source,calls,edges,hooks,risks,definitions,counts);
                } else if(name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                    risks.add("NATIVE_BINARY_NOT_DISASSEMBLED "+artifact+"!"+name);
                }
            }
        }
    }
    private static void annotations(List<AnnotationNode> nodes,Source s,String cl,String method,List<Hook> hooks) {
        if(nodes==null)return;
        for(var a:nodes) if(a.desc.contains("mixin") || a.desc.contains("Mixin"))
            hooks.add(new Hook(s,cl,method,a.desc,JSON.toJson(a.values)));
    }
    private static void inspect(byte[] bytes,Source s,List<Site> calls,List<Edge> edges,List<Hook> hooks,
            List<String> risks,Map<String,Integer> definitions,int[] counts) {
        ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,ClassReader.SKIP_FRAMES);
        definitions.merge(c.name,1,Integer::sum); counts[0]++;
        if(s.entry().startsWith("META-INF/versions/")) risks.add("MULTIRELEASE_VARIANT "+s.artifact()+"!"+s.entry());
        annotations(c.visibleAnnotations,s,c.name,"",hooks); annotations(c.invisibleAnnotations,s,c.name,"",hooks);
        for(MethodNode m:c.methods) {
            counts[1]++; String caller=method(c.name,m.name,m.desc); int line=-1,index=0;
            annotations(m.visibleAnnotations,s,c.name,m.name+m.desc,hooks);
            annotations(m.invisibleAnnotations,s,c.name,m.name+m.desc,hooks);
            if((m.access&Opcodes.ACC_NATIVE)!=0 && !c.name.startsWith("org/lwjgl/"))
                risks.add("JNI_BODY_UNAVAILABLE "+s.artifact()+" "+caller);
            for(AbstractInsnNode i:m.instructions) {
                if(i instanceof LineNumberNode ln)line=ln.line;
                if(i.getOpcode()<0)continue;
                if(i instanceof MethodInsnNode invoke) {
                    target(s,c,m,index,line,invoke.owner,invoke.name,invoke.desc,"invoke-"+i.getOpcode(),calls,edges);
                    if(invoke.owner.startsWith("java/lang/reflect/") || invoke.owner.startsWith("java/lang/invoke/")
                        || invoke.owner.equals("org/lwjgl/system/JNI"))
                        risks.add("INDIRECT_CALL_REQUIRES_RUNTIME "+s.artifact()+" "+caller+" @"+index);
                } else if(i instanceof InvokeDynamicInsnNode dynamic) {
                    boolean found=false;
                    for(Object arg:dynamic.bsmArgs) if(arg instanceof Handle h) {
                        target(s,c,m,index,line,h.getOwner(),h.getName(),h.getDesc(),"method-handle",calls,edges);found=true;
                    }
                    if(!found && !dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory"))
                        risks.add("UNRESOLVED_INVOKEDYNAMIC "+s.artifact()+" "+caller+" @"+index);
                } else if(i instanceof LdcInsnNode ldc && ldc.cst instanceof Handle h) {
                    target(s,c,m,index,line,h.getOwner(),h.getName(),h.getDesc(),"ldc-method-handle",calls,edges);
                }
                index++;
            }
        }
    }
    private static void target(Source s,ClassNode c,MethodNode m,int index,int line,String owner,String name,
            String desc,String invocation,List<Site> calls,List<Edge> edges) {
        String caller=method(c.name,m.name,m.desc),target=method(owner,name,desc);
        edges.add(new Edge(caller,target,invocation));
        if(!gl(owner,name)) return;
        String id=digest((s.sha256()+"\n"+s.entry()+"\n"+caller+"\n"+index+"\n"+target+"\n"+invocation).getBytes(StandardCharsets.UTF_8));
        calls.add(new Site(id,s,c.name,m.name,m.desc,index,line,target,kind(name),"UNKNOWN","UNKNOWN",""));
    }
    /** Exact symbolic reverse edges only: virtual dispatch/reflection are explicitly not inferred. */
    public static Set<String> callers(Scan scan) {
        Map<String,Set<String>> reverse=new HashMap<>();
        for(var e:scan.edges())reverse.computeIfAbsent(e.callee(),k->new HashSet<>()).add(e.caller());
        Set<String> seen=new TreeSet<>();Deque<String> queue=new ArrayDeque<>();
        for(var site:scan.calls())queue.add(method(site.owner(),site.method(),site.descriptor()));
        while(!queue.isEmpty()) {
            String key=queue.remove();if(!seen.add(key))continue;
            queue.addAll(reverse.getOrDefault(key,Set.of()));
        }
        return seen;
    }
    private static String csv(Object value) { return '"'+String.valueOf(value).replace("\"","\"\"")+'"'; }
    public static void main(String[] args) throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("manifest.json output-directory");
        Path manifest=Path.of(args[0]),out=Path.of(args[1]);
        if(Files.exists(out))try(var entries=Files.list(out)) {
            if(entries.findAny().isPresent())throw new IOException("Refusing to overwrite prior inventory evidence: "+out);
        }
        Files.createDirectories(out);
        JsonObject input=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        List<Artifact> artifacts=new ArrayList<>();
        for(var a:input.getAsJsonArray("artifacts")) {
            var o=a.getAsJsonObject();Path p=Path.of(o.get("path").getAsString());
            if(!p.isAbsolute())p=manifest.toAbsolutePath().getParent().resolve(p);
            artifacts.add(new Artifact(p.toString(),o.get("role").getAsString()));
        }
        Scan scan=scan(artifacts);
        if (input.has("decisions")) {
            List<Decision> decisions = new ArrayList<>();
            for (var item : input.getAsJsonArray("decisions")) decisions.add(JSON.fromJson(item, Decision.class));
            scan = review(scan, decisions);
        }
        Files.writeString(out.resolve("inventory.json"),JSON.toJson(scan));
        Files.writeString(out.resolve("manifest.json"),JSON.toJson(input));
        Files.write(out.resolve("possible-callers.txt"),callers(scan));
        try(var writer=Files.newBufferedWriter(out.resolve("calls.csv"))) {
            writer.write("id,artifact,artifact_sha256,role,class,method,descriptor,instruction_ordinal,line,target,category,translation,semantic_change,evidence\n");
            for(var s:scan.calls()) {
                List<Object> values=List.of(s.id(),s.source().artifact(),s.source().sha256(),s.source().role(),s.owner(),s.method(),
                    s.descriptor(),s.instruction(),s.line(),s.target(),s.kind(),s.translation(),s.semanticChange(),s.evidence());
                writer.write(String.join(",",values.stream().map(GlInventory::csv).toList())+"\n");
            }
        }
        var duplicates=scan.classDefinitions().entrySet().stream().filter(e->e.getValue()>1).toList();
        String summary="# OpenGL bytecode inventory\n\nClasses: "+scan.classes()+"; methods: "+scan.methods()+"; GL call sites: "+scan.calls().size()
            +"; symbolic possible callers: "+callers(scan).size()+"; Mixin annotations: "+scan.hooks().size()+".\n\n"
            +"Input scope is exactly manifest.json and nested JARs. Translation and semantic change remain UNKNOWN until supported by reviewed contracts and runtime/final-output evidence. Mixin annotations are candidates, never proof that a draw is translated.\n\n"
            +"Blind spots: native binary bodies; reflection and dynamically generated code; virtual dispatch; final Mixin-transformed classes unless supplied as a separate snapshot; resources/assets outside the manifest; code never reached in a scenario. An empty UNKNOWN runtime count cannot establish unexecuted path coverage.\n\n"
            +"Duplicate class definitions: "+duplicates.size()+". They are retained, not silently resolved to a loader winner. Multi-release variants are also retained.\n\n"
            +"Instruction numbers are executable instruction ordinals, not JVM byte offsets. Artifact hash and method descriptor distinguish overloads/versions. Method references and direct invocations are listed separately. Never mix heavy inventory/call tracing with comparative benchmark timings.\n";
        Files.writeString(out.resolve("REPORT.md"),summary);
        System.out.println(summary);
    }
}
