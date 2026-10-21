package dev.fabricforge.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ModMetadataImpl implements ModMetadata {
    private final JsonObject json;
    private final String id;
    private final Version version;

    public ModMetadataImpl(JsonObject json) {
        this.json = json;
        this.id = json.get("id").getAsString();
        this.version = new VersionImpl(str("version", "1.0.0"));
    }

    private String str(String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    @Override public String getType() { return "fabric"; }
    @Override public String getId() { return id; }
    @Override public Version getVersion() { return version; }
    @Override public String getName() { return str("name", id); }
    @Override public String getDescription() { return str("description", ""); }

    @Override
    public Collection<String> getProvides() {
        List<String> out = new ArrayList<>();
        if (json.has("provides") && json.get("provides").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("provides")) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    @Override
    public Collection<String> getLicense() {
        List<String> out = new ArrayList<>();
        JsonElement l = json.get("license");
        if (l == null) {
            return out;
        }
        if (l.isJsonArray()) {
            for (JsonElement e : l.getAsJsonArray()) {
                out.add(e.getAsString());
            }
        } else {
            out.add(l.getAsString());
        }
        return out;
    }

    @Override
    public boolean containsCustomValue(String key) {
        return json.has("custom") && json.get("custom").isJsonObject() && json.getAsJsonObject("custom").has(key);
    }
}
