import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Static ASM inventory only. Never loads inspected game/mod classes. */
public final class DrawInventory {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final List<Map<String,Object>> sites = new ArrayList<>(), edges = new ArrayList<>(),
        risks = new ArrayList<>(), archives = new ArrayList<>();
    static final Map<String,Integer> definitions = new TreeMap<>();
    static int classes, methods;
    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String layer(String owner) {
        if (owner.startsWith("org/lwjgl/opengl/")) return "raw_gl";
        if (owner.equals("com/mojang/blaze3d/platform/GlStateManager")) return "gl_state";
        if (owner.equals("com/mojang/blaze3d/systems/RenderSystem")) return "render_system";
        if (owner.contains("RenderType") || owner.contains("RenderStateShard")) return "render_state";
        if (owner.startsWith("com/mojang/blaze3d/vertex/") || owner.contains("MultiBufferSource")) return "vertex_or_buffer_source";
        if (owner.equals("net/minecraft/client/gui/GuiGraphics")) return "gui";
        if (owner.contains("RenderTarget") || owner.contains("Framebuffer") || owner.contains("FrameBuffer")) return "target";
        if (owner.contains("ShaderInstance") || owner.contains("ShaderProgram") || owner.contains("PostChain") || owner.contains("PostPass")) return "shader_or_post";
        if (owner.startsWith("foundry/veil/") && (owner.contains("shader/") || owner.contains("post/") || owner.contains("framebuffer/") || owner.endsWith("VeilRenderSystem"))) return "veil_shader_post_target";
        return null;
    }
    static String intent(String owner, String name) {
        String s=(owner+"/"+name).toLowerCase(Locale.ROOT);
        if (s.contains("outline")) return "outline";
        if (s.contains("glint")) return "overlay_or_glint";
        if (s.contains("textseethrough") || s.contains("nametag")) return "priority_text";
        if (s.contains("text") || s.contains("font")) return "formatted_text";
        if (s.contains("emissive") || s.contains("fullbright")) return "fullbright";
        if (s.contains("debug") || s.contains("lines")) return "lines";
        if (s.contains("gui/") || s.contains("guigraphics")) return "ui";
        if (s.contains("post") || s.contains("blur")) return "postprocess";
        if (s.contains("renderer/") || s.contains("render/") || s.contains("particle")) return "world_or_offscreen_unresolved";
        return "other_unresolved";
    }
    static void inspect(byte[] bytes, String project, String version, String origin, String sha, String entry) throws Exception {
        ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,ClassReader.SKIP_FRAMES);
        classes++; definitions.merge(c.name,1,Integer::sum);
        if (entry.startsWith("META-INF/versions/")) risks.add(Map.of("origin",origin,"class",c.name,"risk","multi_release_variant"));
        for (MethodNode m:c.methods) {
            methods++; String caller=c.name+"#"+m.name+m.desc;
            if ((m.access & Opcodes.ACC_NATIVE)!=0) risks.add(Map.of("origin",origin,"method",caller,"risk","native_body_not_inspected"));
            int line=-1, ordinal=0;
            for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
                if (n instanceof LineNumberNode l) line=l.line;
                if (n.getOpcode()<0) continue;
                String owner=null,name=null,descriptor=null,kind=null;
                if (n instanceof MethodInsnNode x) {
                    owner=x.owner;name=x.name;descriptor=x.desc;kind="invoke";
                    edges.add(Map.of("caller",caller,"target",owner+"#"+name+descriptor,"opcode",x.getOpcode()));
                    if (owner.startsWith("java/lang/reflect/") || owner.equals("java/lang/invoke/MethodHandle"))
                        risks.add(Map.of("origin",origin,"method",caller,"line",line,"risk","indirect_dispatch_unresolved"));
                } else if (n instanceof FieldInsnNode x) {
                    owner=x.owner;name=x.name;descriptor=x.desc;kind="field";
                } else if (n instanceof InvokeDynamicInsnNode x) {
                    risks.add(Map.of("origin",origin,"method",caller,"line",line,"risk","invokedynamic_dispatch_unresolved"));
                    for (Object arg:x.bsmArgs) if (arg instanceof Handle h && layer(h.getOwner())!=null)
                        site(project,version,origin,sha,entry,caller,ordinal,line,h.getOwner(),h.getName(),h.getDesc(),"method_reference");
                }
                if (owner!=null && layer(owner)!=null) site(project,version,origin,sha,entry,caller,ordinal,line,owner,name,descriptor,kind);
                ordinal++;
            }
        }
    }
    static void site(String project,String version,String origin,String sha,String entry,String caller,int ordinal,int line,
                     String owner,String name,String descriptor,String kind) throws Exception {
        String id=hash((sha+"\n"+entry+"\n"+caller+"\n"+ordinal+"\n"+owner+"#"+name+descriptor+"\n"+kind).getBytes(StandardCharsets.UTF_8));
        Map<String,Object> s=new LinkedHashMap<>();
        s.put("id",id);s.put("project",project);s.put("version",version);s.put("origin",origin);s.put("artifact_sha256",sha);
        s.put("source",entry);s.put("bytecode_source_line",line);s.put("caller",caller);s.put("instruction_ordinal",ordinal);
        s.put("target",owner+"#"+name+descriptor);s.put("kind",kind);s.put("layer",layer(owner));
        s.put("original_semantics","Invocation/field access identified; arguments/state/reachability require contextual review");
        s.put("intent_candidate",intent(caller,name));s.put("intent_evidence","name-derived lead; not an approved classification");
        s.put("translation","UNKNOWN");s.put("radiance_evidence","");s.put("runtime","static_only");s.put("notes","Virtual dispatch, reflection and final Mixin transformation are unresolved");sites.add(s);
    }
    static void archive(byte[] bytes,String project,String version,String origin,int depth) throws Exception {
        if(depth>8 || bytes.length>256*1024*1024)throw new IOException("Archive exceeds bound: "+origin);
        String sha=hash(bytes); archives.add(Map.of("project",project,"version",version,"origin",origin,"sha256",sha,"depth",depth));
        try (ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(ZipEntry e;(e=z.getNextEntry())!=null;) {
                if(e.isDirectory())continue;String name=e.getName();
                if(name.endsWith(".jar") || name.endsWith(".class")) {
                    byte[] b=z.readNBytes(256*1024*1024+1);
                    if(b.length>256*1024*1024)throw new IOException("Entry exceeds bound: "+name);
                    if(name.endsWith(".jar")) archive(b,project,version,origin+"!"+name,depth+1);
                    else inspect(b,project,version,origin,sha,name);
                } else if(name.endsWith(".dll") || name.endsWith(".so"))
                    risks.add(Map.of("origin",origin+"!"+name,"risk","native_binary_not_disassembled"));
            }
        }
    }
    static String csv(Object value) {return "\""+String.valueOf(value==null?"":value).replace("\"","\"\"")+"\"";}
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("DrawInventory reference-manifest.json new-output-directory");
        Path output=Path.of(args[1]);if(Files.exists(output))throw new IllegalArgumentException("Output exists: "+output);
        JsonObject manifest=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Set<String> roots=Set.of("minecraft","neoforge","create","aeronautics-bundle","sable");
        for(JsonElement el:manifest.getAsJsonArray("artifacts")) {
            JsonObject a=el.getAsJsonObject();String p=a.get("project").getAsString();if(!roots.contains(p))continue;
            Path input=Path.of(a.get("retained").getAsString());byte[] b=Files.readAllBytes(input);
            if(!hash(b).equals(a.get("sha256").getAsString()))throw new IOException("Input hash drift: "+input);
            archive(b,p,a.get("version").getAsString(),input.toString(),0);
        }
        sites.sort(Comparator.comparing(s->(String)s.get("id")));
        Files.createDirectories(output);
        Files.writeString(output.resolve("draw-sites.json"),JSON.toJson(sites));
        Files.writeString(output.resolve("symbolic-edges.json"),JSON.toJson(edges));
        Files.writeString(output.resolve("limitations.json"),JSON.toJson(risks));
        List<String> columns=new ArrayList<>(sites.isEmpty()?List.of():sites.getFirst().keySet());
        try(BufferedWriter w=Files.newBufferedWriter(output.resolve("draw-sites.csv"))) {
            w.write(String.join(",",columns));w.newLine();
            for(var s:sites){w.write(String.join(",",columns.stream().map(k->csv(s.get(k))).toList()));w.newLine();}
        }
        Map<String,Object> scope=new LinkedHashMap<>();scope.put("classes",classes);scope.put("methods",methods);scope.put("sites",sites.size());
        scope.put("archives",archives);scope.put("duplicate_class_definitions",definitions.entrySet().stream().filter(e->e.getValue()>1).map(e->Map.of("class",e.getKey(),"definitions",e.getValue())).toList());
        scope.put("limits",List.of("Static direct-owner inventory, not a proof of all runtime draw intent or equivalent pixels",
            "Field accesses include reads/writes and declarations are in the companion source inventory",
            "Nested project/version inherits distribution parent; use reference manifest for embedded identities",
            "Bytecode source lines differ from decompiler output lines; use caller signature to join",
            "Resource/shader declarations and contextual state are in the companion source inventory"));
        Files.writeString(output.resolve("scope.json"),JSON.toJson(scope));System.out.println(JSON.toJson(Map.of("classes",classes,"methods",methods,"sites",sites.size())));
    }
}
