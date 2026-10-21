package dev.fabricforge.remap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts a Fabric access widener (intermediary namespace) into a Forge access transformer config. */
public final class AccessWidener {
    private AccessWidener() {}

    /** @return AT file text, or null if the widener cannot be converted (reason added to warnings). */
    public static String toAccessTransformer(String widener, FabricRemapper r, List<String> warnings) {
        String[] lines = widener.split("\\R");
        if (lines.length == 0) {
            return null;
        }
        String[] header = lines[0].trim().split("\\s+");
        if (header.length < 3 || !header[0].equals("accessWidener")) {
            warnings.add("not a valid access widener header: " + lines[0]);
            return null;
        }
        if (!header[2].equals("intermediary")) {
            warnings.add("access widener namespace '" + header[2] + "' is not supported (only intermediary)");
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("\\s+");
            String access = t[0].startsWith("transitive-") ? t[0].substring("transitive-".length()) : t[0];
            if (t.length < 3) {
                warnings.add("bad line: " + line);
                continue;
            }
            String kind = t[1];
            String owner = r.map(t[2]);
            String dotted = owner.replace('/', '.');
            switch (kind) {
                case "class" -> out.add(classAccess(access) + " " + dotted);
                case "method" -> {
                    if (t.length < 5) {
                        warnings.add("bad method line: " + line);
                        continue;
                    }
                    String name = t[3].startsWith("<") ? t[3] : r.mapMethodName(t[2], t[3], t[4]);
                    out.add(methodAccess(access) + " " + dotted + " " + name + r.mapMethodDesc(t[4]));
                }
                case "field" -> {
                    if (t.length < 5) {
                        warnings.add("bad field line: " + line);
                        continue;
                    }
                    out.add(fieldAccess(access) + " " + dotted + " " + r.mapFieldName(t[2], t[3], t[4]));
                }
                default -> warnings.add("unknown target kind: " + line);
            }
        }
        StringBuilder sb = new StringBuilder("# generated from a Fabric access widener by Fabric-on-Forge\n");
        for (String l : out) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static String classAccess(String a) {
        return a.equals("extendable") ? "public-f" : "public";
    }

    private static String methodAccess(String a) {
        return a.equals("extendable") ? "protected-f" : "public";
    }

    private static String fieldAccess(String a) {
        return a.equals("mutable") ? "public-f" : "public";
    }
}
