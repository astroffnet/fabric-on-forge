package dev.fabricforge.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ModContainerImpl implements ModContainer {
    private final String forgeId;
    private final JsonObject json;
    private final Path jar;
    private final ModMetadataImpl metadata;
    private volatile ClassLoader loader;
    private FileSystem fs;

    public ModContainerImpl(String forgeId, JsonObject json, Path jar) {
        this.forgeId = forgeId;
        this.json = json;
        this.jar = jar;
        this.metadata = new ModMetadataImpl(json);
    }

    public String forgeId() { return forgeId; }
    public ClassLoader loader() { return loader; }
    public void setLoader(ClassLoader loader) { this.loader = loader; }

    /** Class names declared for an entrypoint key. "Class::member" references are not supported yet. */
    public List<String> entrypointClasses(String key) {
        List<String> out = new ArrayList<>();
        if (!json.has("entrypoints") || !json.get("entrypoints").isJsonObject()) {
            return out;
        }
        JsonElement list = json.getAsJsonObject("entrypoints").get(key);
        if (list == null) {
            return out;
        }
        if (list.isJsonArray()) {
            for (JsonElement e : list.getAsJsonArray()) {
                add(out, e);
            }
        } else {
            add(out, list);
        }
        return out;
    }

    private static void add(List<String> out, JsonElement e) {
        String value = null;
        if (e.isJsonPrimitive()) {
            value = e.getAsString();
        } else if (e.isJsonObject() && e.getAsJsonObject().has("value")) {
            value = e.getAsJsonObject().get("value").getAsString();
        }
        if (value != null) {
            out.add(value);
        }
    }

    @Override public ModMetadata getMetadata() { return metadata; }

    @Override
    public synchronized Path getRootPath() {
        try {
            if (fs == null) {
                fs = FileSystems.newFileSystem(jar, (ClassLoader) null);
            }
            return fs.getPath("/");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public List<Path> getRootPaths() { return List.of(getRootPath()); }

    @Override
    public Optional<Path> findPath(String file) {
        Path p = getRootPath().resolve(file.startsWith("/") ? file.substring(1) : file);
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }

    @Override public Optional<ModContainer> getContainingMod() { return Optional.empty(); }
    @Override public Collection<ModContainer> getContainedMods() { return List.of(); }
}
