package com.radiance.client;

import com.mojang.logging.LogUtils;
import com.radiance.bootstrap.BootstrapState;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.vulkan.VertexArrayProxy;
import com.radiance.client.proxy.vulkan.WindowProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.platform.RadiancePlatform;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;

public class RadianceClient {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static Path radianceDir;
    private static boolean initialized;

    /**
     * Existing GAME-side JNI owners. The SERVICE bootstrap loads the shared
     * library once; these classes are rebound to its already-exported symbols
     * when the GAME module starts.
     */
    private static final Class<?>[] GAME_NATIVE_OWNERS = {
        Options.class,
        Pipeline.class,
        BufferProxy.class,
        DrawCommandProxy.Overlay.class,
        FramebufferProxy.class,
        com.radiance.client.proxy.vulkan.UiPathTracingProxy.class,
        PipelineStateProxy.ClearState.class,
        PipelineStateProxy.ColorBlendState.class,
        PipelineStateProxy.DepthStencilState.class,
        PipelineStateProxy.DiagramState.class,
        PipelineStateProxy.RasterizationState.class,
        PipelineStateProxy.ViewportState.class,
   RendererProxy.class,
   ShaderProxy.class,
   TextureProxy.class,
   VertexArrayProxy.class,
   WindowProxy.class,
        ChunkProxy.class,
        EntityProxy.class,
        com.radiance.client.proxy.world.NativeInstancingProxy.class,
        PlayerProxy.class
    };

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        new RadianceClient().initializeInternal();
        initialized = true;
    }

    private void initializeInternal() {
        Path mcBaseDir = RadiancePlatform.gameDirectory();
        {
            // The SERVICE provider has already extracted this content-addressed
            // runtime and loaded the native library. Keep the stable GAME data
            // directory for user options/pipelines, copy only its shader/module
            // payload, and register existing GAME JNI owners without another
            // System.load call.
            radianceDir = mcBaseDir.resolve("radiance");
            try {
                Files.createDirectories(radianceDir);
                // This also loads the SERVICE-owned library when the user disabled
                // the early window; renderer creation then remains in the normal game path.
                Path runtimeDirectory = BootstrapState.prepareGameRuntime(mcBaseDir);
                copyFolderFromPath(runtimeDirectory.resolve("shaders"), radianceDir.resolve("shaders"));
                if (Files.isDirectory(runtimeDirectory.resolve("dlss"))) {
                    copyFolderFromPath(runtimeDirectory.resolve("dlss"), radianceDir.resolve("dlss"));
                }
                copyFolderFromResource(radianceDir.resolve("modules"), Path.of("modules"));
            } catch (IOException e) {
                throw new RuntimeException("Unable to prepare the active Radiance GAME resources", e);
            }
            bindGameNatives();
        }

        RendererProxy.initFolderPath(radianceDir.toAbsolutePath().toString());
        Pipeline.initFolderPath(radianceDir);

        Options.readOptions();

        Pipeline.reloadAllModuleEntries();
    }

    private void initializeStandaloneRuntime(Path mcBaseDir) {
        radianceDir = mcBaseDir.resolve("radiance");
        try {
            Files.createDirectories(radianceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Keep the existing standalone path for environments without the
        // SERVICE bootstrap.
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("windows")) {
            Path libTargetPath = radianceDir.resolve("core.lib");
            Path libResourcePath = Path.of("core.lib");
            copyFileFromResource(libTargetPath, libResourcePath);

            Path dllTargetPath = radianceDir.resolve("core.dll");
            Path dllResourcePath = Path.of("core.dll");
            copyFileFromResource(dllTargetPath, dllResourcePath);
            Path xessPath = radianceDir.resolve("libxess.dll");
            Path xessDx11Path = radianceDir.resolve("libxess_dx11.dll");
            Path xessFgPath = radianceDir.resolve("libxess_fg.dll");
            copyOptionalFileFromResource(xessPath, Path.of("libxess.dll"));
            // currently not used, can be used later for fg
            copyOptionalFileFromResource(xessDx11Path, Path.of("libxess_dx11.dll"));
            copyOptionalFileFromResource(xessFgPath, Path.of("libxess_fg.dll"));

            loadOptionalLibrary(xessPath);
            System.load(dllTargetPath.toAbsolutePath().toString());
        } else if (osName.toLowerCase().contains("linux")) {
            Path soTargetPath = radianceDir.resolve("libcore.so");
            Path soResourcePath = Path.of("libcore.so");
            copyFileFromResource(soTargetPath, soResourcePath);
            System.load(soTargetPath.toAbsolutePath().toString());
        } else {
            throw new RuntimeException("The OS " + osName + " is not supported");
        }

        Path shaderTargetPath = radianceDir.resolve("shaders");
        Path shaderResourcePath = Path.of("shaders");
        copyFolderFromResource(shaderTargetPath, shaderResourcePath);

        Path moduleTargetPath = radianceDir.resolve("modules");
        Path moduleResourcePath = Path.of("modules");
        copyFolderFromResource(moduleTargetPath, moduleResourcePath);
    }

    private static void bindGameNatives() {
        for (Class<?> owner : GAME_NATIVE_OWNERS) {
            Method[] nativeMethods = Arrays.stream(owner.getDeclaredMethods())
                    .filter(method -> Modifier.isNative(method.getModifiers()))
                    .sorted(Comparator.comparing(Method::getName)
                            .thenComparing(RadianceClient::methodDescriptor))
                    .toArray(Method[]::new);
            if (nativeMethods.length == 0) {
                throw new IllegalStateException("No native methods found for JNI owner " + owner.getName());
            }

            Map<String, Integer> methodsByName = new HashMap<>();
            for (Method method : nativeMethods) {
                methodsByName.merge(method.getName(), 1, Integer::sum);
            }

            String[] names = new String[nativeMethods.length];
            String[] descriptors = new String[nativeMethods.length];
            String[] symbols = new String[nativeMethods.length];
            for (int i = 0; i < nativeMethods.length; i++) {
                Method method = nativeMethods[i];
                String descriptor = methodDescriptor(method);
                names[i] = method.getName();
                descriptors[i] = descriptor;
                symbols[i] = jniSymbol(owner, method, descriptor,
                        methodsByName.get(method.getName()) > 1);
            }

            BootstrapState.bindGameNatives(owner, names, descriptors, symbols);
        }
    }

    private static String methodDescriptor(Method method) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            appendDescriptor(descriptor, parameter);
        }
        descriptor.append(')');
        appendDescriptor(descriptor, method.getReturnType());
        return descriptor.toString();
    }

    private static void appendDescriptor(StringBuilder output, Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) output.append('V');
            else if (type == boolean.class) output.append('Z');
            else if (type == byte.class) output.append('B');
            else if (type == char.class) output.append('C');
            else if (type == short.class) output.append('S');
            else if (type == int.class) output.append('I');
            else if (type == long.class) output.append('J');
            else if (type == float.class) output.append('F');
            else if (type == double.class) output.append('D');
            else throw new IllegalArgumentException("Unknown primitive JNI type " + type);
        } else if (type.isArray()) {
            output.append(type.getName().replace('.', '/'));
        } else {
            output.append('L').append(type.getName().replace('.', '/')).append(';');
        }
    }

    private static String jniSymbol(Class<?> owner, Method method, String descriptor,
                                    boolean overloaded) {
        String symbol = "Java_" + jniEncode(owner.getName()) + "_" + jniEncode(method.getName());
        if (overloaded) {
            int end = descriptor.indexOf(')');
            symbol += "__" + jniEncode(descriptor.substring(1, end));
        }
        return symbol;
    }

    private static String jniEncode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '.' -> encoded.append('_');
                case '/' -> encoded.append('_');
                case '_' -> encoded.append("_1");
                case ';' -> encoded.append("_2");
                case '[' -> encoded.append("_3");
                case '$' -> encoded.append("_00024");
                default -> {
                    if (character >= 0x80) {
                        encoded.append(String.format("_0%04x", (int) character));
                    } else {
                        encoded.append(character);
                    }
                }
            }
        }
        return encoded.toString();
    }

    public void copyFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                throw new IOException("Cannot find target path: " + resourcePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyOptionalFileFromResource(Path targetPath, Path resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(toResourcePath(resourcePath))) {
            if (is == null) {
                return;
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadOptionalLibrary(Path path) {
        if (Files.exists(path)) {
            System.load(path.toAbsolutePath().toString());
        }
    }

    public String toResourcePath(Path path) {
        String joined = StreamSupport.stream(path.spliterator(), false).map(Object::toString)
            .collect(Collectors.joining("/"));
        return "/" + joined;
    }

    public void copyFolderFromResource(Path targetPath, Path resourcePath) {
        String resourcePathStr = toResourcePath(resourcePath);
        URL url = getClass().getResource(resourcePathStr);

        if (url == null) {
            throw new RuntimeException("Resource folder not found: " + resourcePathStr);
        }

        try {
            URI uri = url.toURI();

            if ("jar".equals(uri.getScheme())) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                URI jarFileUri = conn.getJarFileURL().toURI();
                URI jarFsUri = URI.create("jar:" + jarFileUri);

                FileSystem fs = null;
                boolean created = false;
                try {
                    try {
                        fs = FileSystems.getFileSystem(jarFsUri);
                    } catch (FileSystemNotFoundException e) {
                        fs = FileSystems.newFileSystem(jarFsUri, Collections.emptyMap());
                        created = true;
                    }

                    Path root = fs.getPath(resourcePathStr);
                    walkAndCopy(root, targetPath, resourcePath);
                } finally {
                    if (created) {
                        try {
                            fs.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            } else {
                Path root = Paths.get(uri);
                walkAndCopy(root, targetPath, resourcePath);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to copy resource folder", e);
        }
    }

    private static void copyFolderFromPath(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Runtime resource directory is missing: " + sourceRoot);
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                Path target = targetRoot.resolve(sourceRoot.relativize(source).toString());
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to copy runtime resource " + source, e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
    }

    private void walkAndCopy(Path walkRoot, Path targetRoot, Path baseResourcePath)
        throws IOException {
        try (Stream<Path> stream = Files.walk(walkRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                String relativePathStr = walkRoot.relativize(source).toString();
                Path targetFile = targetRoot.resolve(relativePathStr);
                Path childResourcePath = baseResourcePath.resolve(relativePathStr);
                copyFileFromResource(targetFile, childResourcePath);
            });
        }
    }
}
