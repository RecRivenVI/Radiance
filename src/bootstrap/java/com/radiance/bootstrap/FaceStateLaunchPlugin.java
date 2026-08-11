package com.radiance.bootstrap;

import cpw.mods.modlauncher.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * SERVICE transformation: only actual GL face-state call sites in client GAME
 * classes.
 */
public final class FaceStateLaunchPlugin implements ITransformationService {
    private static volatile boolean client;
    static void enableForClient() {
        client = true;
    }
    public String name() {
        return "radiance_face_state";
    }
    public void initialize(IEnvironment environment) {}
    public void onLoad(IEnvironment environment, Set<String> otherServices) {}
    public static boolean eligible(String name) {
        return !name.startsWith("com/radiance/") && !name.startsWith("org/lwjgl/") &&
            // These methods already have exact GL redirects. Preserve their
            // injection targets.
            !name.startsWith("com/mojang/blaze3d/platform/GlStateManager")
            && !name.startsWith("cpw/") && !name.startsWith("net/neoforged/fml/")
            && !name.startsWith("java/") && !name.startsWith("jdk/");
    }
    public List<? extends ITransformer<?>> transformers() {
        if (!client)
            return List.of();
        Set<ITransformer.Target<ClassNode>> targets = new HashSet<>();
        // ModLauncher asks for transformers after FML has discovered its GAME mod
        // files. SERVICE lives outside BOOT, so ILaunchPluginService would never be
        // discovered here.
        for (var info : FMLLoader.getLoadingModList().getModFiles()) {
            var root = info.getFile().getSecureJar().getRootPath();
            try {
                targets.addAll(collectTargets(root));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot enumerate client face-state call sites: "
                        + info.getFile().getFileName(),
                    failure);
            }
        }
        org.slf4j.LoggerFactory.getLogger("RadianceBootstrap")
            .info("Face-state translation registered for {} GAME classes", targets.size());
        return List.of(new ITransformer<ClassNode>() {
            public ClassNode transform(ClassNode node, ITransformerVotingContext context) {
                rewrite(node);
                return node;
            }
            public TransformerVoteResult castVote(ITransformerVotingContext context) {
                return TransformerVoteResult.YES;
            }
            public Set<ITransformer.Target<ClassNode>> targets() {
                return targets;
            }
            public TargetType<ClassNode> getTargetType() {
                return TargetType.CLASS;
            }
        });
    }
    static Set<ITransformer.Target<ClassNode>> collectTargets(Path root) throws java.io.IOException {
        Set<ITransformer.Target<ClassNode>> targets = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - 6);
                if (!eligible(name))
                    continue;
                ClassNode node = new ClassNode();
                // Read bytes only: optional compatibility classes can reference absent mods.
                // Registering even a no-op target makes ModLauncher recompute frames and
                // resolve those types before Mixin can reject an inapplicable mixin.
                new ClassReader(Files.readAllBytes(path)).accept(
                    node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (hasFaceCalls(node))
                    targets.add(ITransformer.Target.targetClass(name.replace('/', '.')));
            }
        }
        return targets;
    }
    private static boolean isFaceCall(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC
            && (call.owner.equals("org/lwjgl/opengl/GL11")
                || call.owner.equals("org/lwjgl/opengl/GL11C"))
            && call.desc.equals("(I)V")
            && (call.name.equals("glCullFace") || call.name.equals("glFrontFace")
                || call.name.equals("glEnable") || call.name.equals("glDisable"));
    }
    private static boolean hasFaceCalls(ClassNode node) {
        for (var method : node.methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && isFaceCall(call))
                    return true;
        return false;
    }
    public static boolean rewrite(ClassNode node) {
        if (!eligible(node.name))
            return false;
        boolean changed = false;
        for (var method : node.methods)
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && isFaceCall(call)) {
                    call.owner = "com/radiance/client/render/DirectFaceState";
                    changed = true;
                }
            }
        return changed;
    }
}
