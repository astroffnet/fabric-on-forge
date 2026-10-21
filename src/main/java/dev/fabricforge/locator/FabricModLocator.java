package dev.fabricforge.locator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.fabricforge.bridge.FabricModRegistry;
import dev.fabricforge.bridge.ModContainerImpl;
import dev.fabricforge.remap.AccessWidener;
import dev.fabricforge.remap.FabricRemapper;
import dev.fabricforge.remap.MappingData;
import dev.fabricforge.remap.MappingProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Discovers Fabric mod jars in <gamedir>/fabric-mods and exposes them to FML as Forge mods:
 *  - generates META-INF/mods.toml (javafml) and an @Mod entry class that starts the Fabric entrypoints
 *  - remaps all classes, annotation strings and mixin refmaps from intermediary to Forge runtime names
 *  - registers mixin configs through the MixinConfigs manifest attribute
 *  - converts the access widener into META-INF/accesstransformer.cfg
 */
public class FabricModLocator extends AbstractJarFileModLocator {
    private static final Logger LOG = LogManager.getLogger("FabricOnForge");
    /** Provided by our built-in emulation; the real jars must not be loaded. */
    private static final Pattern BUILTIN = Pattern.compile("fabric-api|fabric-.+-v\\d+|fabricloader");

    @Override
    public String name() {
        return "fabric-on-forge";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public Stream<Path> scanCandidates() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path source = gameDir.resolve("fabric-mods");
        Path cache = gameDir.resolve(".fabric-on-forge").resolve("wrapped");
        try {
            Files.createDirectories(source);
            Files.createDirectories(cache);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Path> jars;
        try (Stream<Path> files = Files.list(source)) {
            jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        FabricRemapper remapper = null;
        if (!jars.isEmpty()) {
            try {
                MappingData data = MappingProvider.load(gameDir.resolve(".fabric-on-forge").resolve("mappings"));
                LOG.info("Loaded mappings: {} classes, {} methods, {} fields ({} conflicts)",
                    data.classCount(), data.methodCount(), data.fieldCount(), data.conflicts());
                remapper = new FabricRemapper(data);
            } catch (Throwable t) {
                LOG.error("Could not load intermediary->SRG mappings; Fabric mods will NOT be remapped", t);
            }
        }

        List<Path> result = new ArrayList<>();
        for (Path jar : jars) {
            try {
                Path wrapped = wrap(jar, cache, remapper);
                if (wrapped != null) {
                    result.add(wrapped);
                }
            } catch (Exception e) {
                LOG.error("Failed to process Fabric mod {}", jar.getFileName(), e);
            }
        }
        LOG.info("Found {} Fabric mod(s) in {}", result.size(), source);
        return result.stream();
    }

    private static boolean isClient() {
        try {
            return FMLLoader.getDist() == Dist.CLIENT;
        } catch (Throwable t) {
            return true;
        }
    }

    private static Path wrap(Path jar, Path cache, FabricRemapper remapper) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry meta = zip.getEntry("fabric.mod.json");
            if (meta == null) {
                LOG.warn("{} has no fabric.mod.json, skipping", jar.getFileName());
                return null;
            }
            JsonObject json;
            try (InputStream in = zip.getInputStream(meta)) {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            String fabricId = str(json, "id", null);
            if (fabricId != null && BUILTIN.matcher(fabricId).matches()) {
                LOG.info("Skipping '{}' ({}): provided by the built-in Fabric emulation", fabricId, jar.getFileName());
                return null;
            }
            String id = sanitizeId(fabricId);
            if (id == null) {
                LOG.warn("{} has no valid mod id, skipping", jar.getFileName());
                return null;
            }
            String version = str(json, "version", "1.0.0");
            String name = str(json, "name", id);
            String description = str(json, "description", "Fabric mod loaded by Fabric-on-Forge");

            // ---- mixins
            List<String> mixinConfigs = new ArrayList<>();
            Set<String> refmaps = new HashSet<>();
            collectMixins(zip, json, id, mixinConfigs, refmaps);

            // ---- access widener -> access transformer
            String accessTransformer = null;
            String awName = str(json, "accessWidener", null);
            if (awName != null) {
                ZipEntry awEntry = zip.getEntry(awName);
                if (awEntry == null) {
                    LOG.warn("[{}] access widener {} not found in jar", id, awName);
                } else if (remapper == null) {
                    LOG.warn("[{}] access widener skipped (no mappings)", id);
                } else {
                    try (InputStream in = zip.getInputStream(awEntry)) {
                        List<String> warnings = new ArrayList<>();
                        accessTransformer = AccessWidener.toAccessTransformer(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8), remapper, warnings);
                        warnings.forEach(w -> LOG.warn("[{}] access widener: {}", id, w));
                    }
                }
            }

            String toml = "modLoader=\"javafml\"\n"
                + "loaderVersion=\"[47,)\"\n"
                + "license=\"" + esc(str(json, "license", "Unknown")) + "\"\n\n"
                + "[[mods]]\n"
                + "modId=\"" + id + "\"\n"
                + "version=\"" + esc(version) + "\"\n"
                + "displayName=\"" + esc(name) + "\"\n"
                + "description='''" + description.replace("'''", "'") + "'''\n";

            // manifest: keep the original main attributes, add MixinConfigs
            Manifest manifest = new Manifest();
            ZipEntry mfEntry = zip.getEntry("META-INF/MANIFEST.MF");
            if (mfEntry != null) {
                try (InputStream in = zip.getInputStream(mfEntry)) {
                    manifest.getMainAttributes().putAll(new Manifest(in).getMainAttributes());
                } catch (IOException ignored) {
                    // broken manifest: start clean
                }
            }
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            if (!mixinConfigs.isEmpty()) {
                manifest.getMainAttributes().putValue("MixinConfigs", String.join(",", mixinConfigs));
            }

            Path out = cache.resolve(id + "-" + jar.getFileName());
            Set<String> written = new HashSet<>();
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
                zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                manifest.write(zos);
                zos.closeEntry();
                written.add("META-INF/MANIFEST.MF");

                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String n = e.getName();
                    String upper = n.toUpperCase();
                    boolean signature = upper.startsWith("META-INF/")
                        && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
                    boolean replaced = n.equals("META-INF/mods.toml")
                        || (accessTransformer != null && n.equals("META-INF/accesstransformer.cfg"));
                    if (signature || replaced || !written.add(n)) {
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(n));
                    if (!e.isDirectory()) {
                        try (InputStream in = zip.getInputStream(e)) {
                            byte[] bytes = null;
                            if (remapper != null && n.endsWith(".class")) {
                                bytes = in.readAllBytes();
                                try {
                                    bytes = remapper.remap(bytes);
                                } catch (Throwable t) {
                                    LOG.error("Failed to remap {} in {}", n, jar.getFileName(), t);
                                }
                            } else if (remapper != null && refmaps.contains(n)) {
                                bytes = in.readAllBytes();
                                try {
                                    bytes = rewriteRefmap(bytes, remapper);
                                } catch (Throwable t) {
                                    LOG.error("Failed to rewrite refmap {} in {}", n, jar.getFileName(), t);
                                }
                            }
                            if (bytes != null) {
                                zos.write(bytes);
                            } else {
                                in.transferTo(zos);
                            }
                        }
                    }
                    zos.closeEntry();
                }

                if (accessTransformer != null) {
                    zos.putNextEntry(new ZipEntry("META-INF/accesstransformer.cfg"));
                    zos.write(accessTransformer.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                String entryInternal = "dev/fabricforge/generated/" + id + "/Entry";
                zos.putNextEntry(new ZipEntry(entryInternal + ".class"));
                zos.write(generateEntryClass(id, entryInternal));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("META-INF/mods.toml"));
                zos.write(toml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            FabricModRegistry.register(new ModContainerImpl(id, json, jar));
            LOG.info("Wrapped Fabric mod '{}' ({}) as Forge mod id '{}' [{} mixin config(s){}]",
                name, version, id, mixinConfigs.size(), accessTransformer != null ? ", access transformer" : "");
            return out;
        }
    }

    /** Reads the "mixins" entry of fabric.mod.json, filters by environment, and finds the refmaps. */
    private static void collectMixins(ZipFile zip, JsonObject json, String id, List<String> configs, Set<String> refmaps) {
        if (!json.has("mixins") || !json.get("mixins").isJsonArray()) {
            return;
        }
        boolean client = isClient();
        for (JsonElement e : json.getAsJsonArray("mixins")) {
            String config = null;
            String env = "*";
            if (e.isJsonPrimitive()) {
                config = e.getAsString();
            } else if (e.isJsonObject() && e.getAsJsonObject().has("config")) {
                config = e.getAsJsonObject().get("config").getAsString();
                env = str(e.getAsJsonObject(), "environment", "*");
            }
            if (config == null || (env.equals("client") && !client) || (env.equals("server") && client)) {
                continue;
            }
            ZipEntry entry = zip.getEntry(config);
            if (entry == null) {
                LOG.warn("[{}] mixin config {} not found in jar", id, config);
                continue;
            }
            configs.add(config);
            try (InputStream in = zip.getInputStream(entry)) {
                JsonObject cfg = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (cfg.has("refmap")) {
                    refmaps.add(cfg.get("refmap").getAsString());
                }
            } catch (Exception ex) {
                LOG.warn("[{}] could not read mixin config {}: {}", id, config, ex.toString());
            }
        }
        if (zip.getEntry("com/llamalad7/mixinextras/MixinExtrasBootstrap.class") == null
            && json.has("depends") && json.get("depends").toString().contains("mixinextras")) {
            LOG.warn("[{}] depends on MixinExtras, which is not provided", id);
        }
    }

    /**
     * Fabric refmaps hold intermediary names. Rewrite them to Forge runtime names and expose them
     * under both the default context ("mappings") and "searge".
     */
    private static byte[] rewriteRefmap(byte[] bytes, FabricRemapper remapper) {
        JsonObject src = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject base = new JsonObject();
        if (src.has("data") && src.get("data").isJsonObject()
            && src.getAsJsonObject("data").has("named:intermediary")) {
            base = src.getAsJsonObject("data").getAsJsonObject("named:intermediary");
        } else if (src.has("mappings") && src.get("mappings").isJsonObject()) {
            base = src.getAsJsonObject("mappings");
        }
        JsonObject mapped = mapStrings(base, remapper).getAsJsonObject();
        JsonObject out = new JsonObject();
        out.add("mappings", mapped);
        JsonObject data = new JsonObject();
        data.add("searge", mapped.deepCopy());
        out.add("data", data);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonElement mapStrings(JsonElement e, FabricRemapper remapper) {
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                o.add(en.getKey(), mapStrings(en.getValue(), remapper));
            }
            return o;
        }
        if (e.isJsonArray()) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (JsonElement x : e.getAsJsonArray()) {
                a.add(mapStrings(x, remapper));
            }
            return a;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(remapper.mapNames(e.getAsString()));
        }
        return e;
    }

    /** public class Entry { @Mod(id) public Entry() { Bridge.onModConstruct(id, Entry.class); } } */
    private static byte[] generateEntryClass(String id, String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null);
        AnnotationVisitor av = cw.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        av.visit("value", id);
        av.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitLdcInsn(id);
        mv.visitLdcInsn(Type.getObjectType(internalName));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/fabricforge/bridge/Bridge", "onModConstruct",
            "(Ljava/lang/String;Ljava/lang/Class;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /** Forge mod ids: ^[a-z][a-z0-9_]{1,63}$ */
    private static String sanitizeId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            s = "f" + s;
        }
        if (s.length() < 2) {
            s = s + "_";
        }
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
