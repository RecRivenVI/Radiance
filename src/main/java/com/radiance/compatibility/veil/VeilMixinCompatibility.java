package com.radiance.compatibility.veil;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Keeps Veil's merged OpenGL-only handlers outside Radiance's generic mixin plugin.
 */
public final class VeilMixinCompatibility {

    private static final Map<String, Set<String>> VEIL_VOID_HANDLER_WHITELIST = Map.of(
        "net.minecraft.client.renderer.ShaderInstance", Set.of(
            "$veil$updateLocations"
        ),
        "net.minecraft.client.renderer.GameRenderer", Set.of(
            "$veil$replaceShaders",
            "$veil$reloadShaders"
        )
    );

    private VeilMixinCompatibility() {
    }

    public static void postApply(String targetClassName, ClassNode targetClass) {
        for (MethodNode method : targetClass.methods) {
            if (targetClassName.equals("net.minecraft.client.renderer.GameRenderer")
                && method.name.endsWith("$veil$getShader")
                && method.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")) {
                // Veil 4.3 replaces a vanilla shader with one owned by its OpenGL renderer.
                // Keep the original instance for Radiance's shader translation instead.
                clearMethod(method);
                int shaderArgument = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, shaderArgument));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                method.maxStack = 1;
            } else if (isWhitelistedVoidHandler(targetClassName, method)) {
                // These remaining handlers perform raw GL uniform reflection or vanilla
                // program relinking; the corresponding native bridge must precede restoring them.
                clearMethod(method);
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.maxStack = 0;
            }
        }
    }

    private static boolean isWhitelistedVoidHandler(String targetClassName, MethodNode method) {
        Set<String> handlers = VEIL_VOID_HANDLER_WHITELIST.get(targetClassName);
        return handlers != null && method.desc.endsWith(")V")
            && handlers.stream().anyMatch(method.name::contains);
    }

    private static void clearMethod(MethodNode method) {
        method.instructions.clear();
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
    }
}
