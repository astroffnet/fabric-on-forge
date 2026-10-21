package dev.fabricforge.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Combined mapping: Fabric intermediary -> Forge runtime names.
 *  - classes: intermediary class -> Forge runtime class (Mojang name)
 *  - methods/fields: intermediary name -> SRG name (global; intermediary and SRG both keep one
 *    name per override family, so no owner lookup is needed)
 * Built by joining  intermediary tiny v2 (official->intermediary)  with  MCPConfig joined.tsrg (official->srg).
 */
public final class MappingData {
    private final Map<String, String> classes = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private int conflicts;

    public String mapClass(String intermediary) { return classes.get(intermediary); }
    public String mapMethod(String intermediary) { return methods.get(intermediary); }
    public String mapField(String intermediary) { return fields.get(intermediary); }
    public int classCount() { return classes.size(); }
    public int methodCount() { return methods.size(); }
    public int fieldCount() { return fields.size(); }
    public int conflicts() { return conflicts; }

    public static MappingData build(BufferedReader tiny, BufferedReader tsrg) throws IOException {
        // ---- 1. TSRG (v1 or v2): obf -> srg
        Map<String, String> tsrgClass = new HashMap<>();
        Map<String, String> tsrgMethod = new HashMap<>(); // "obfOwner obfName obfDesc" -> srg
        Map<String, String> tsrgField = new HashMap<>();  // "obfOwner obfName" -> srg
        String line;
        String cur = null;
        boolean first = true;
        while ((line = tsrg.readLine()) != null) {
            if (first) {
                first = false;
                if (line.startsWith("tsrg2")) {
                    continue;
                }
            }
            if (line.isEmpty() || line.startsWith("\t\t")) {
                continue;
            }
            if (line.charAt(0) != '\t') {
                String[] p = line.split(" ");
                if (p.length >= 2) {
                    cur = p[0];
                    tsrgClass.put(p[0], p[1]);
                }
                continue;
            }
            if (cur == null) {
                continue;
            }
            String[] p = line.substring(1).split(" ");
            if (p.length >= 3 && p[1].startsWith("(")) {
                tsrgMethod.put(cur + " " + p[0] + " " + p[1], p[2]);
            } else if (p.length >= 2) {
                tsrgField.put(cur + " " + p[0], p[1]);
            }
        }

        // ---- 2. Tiny v2: official -> intermediary, joined with the above
        String header = tiny.readLine();
        if (header == null) {
            throw new IOException("Empty intermediary mappings");
        }
        String[] h = header.split("\t");
        if (h.length < 5 || !h[0].equals("tiny") || !h[1].equals("2")) {
            throw new IOException("Unsupported tiny header: " + header);
        }
        int n = h.length - 3;
        int off = -1;
        int inter = -1;
        for (int i = 0; i < n; i++) {
            if (h[3 + i].equals("official")) off = i;
            if (h[3 + i].equals("intermediary")) inter = i;
        }
        if (off != 0 || inter < 0) {
            throw new IOException("Expected namespaces official -> intermediary, got: " + header);
        }

        MappingData d = new MappingData();
        String curObf = null;
        while ((line = tiny.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) != '\t') {
                String[] p = line.split("\t");
                if (p[0].equals("c") && p.length >= 1 + n) {
                    curObf = p[1 + off];
                    d.classes.put(p[1 + inter], tsrgClass.getOrDefault(curObf, curObf));
                } else {
                    curObf = null;
                }
                continue;
            }
            if (curObf == null || line.startsWith("\t\t")) {
                continue;
            }
            String[] p = line.substring(1).split("\t");
            if (p.length < 2 + n) {
                continue;
            }
            String obfName = p[2 + off];
            String interName = p[2 + inter];
            if (p[0].equals("m")) {
                String srg = tsrgMethod.get(curObf + " " + obfName + " " + p[1]);
                d.put(d.methods, interName, srg != null ? srg : obfName);
            } else if (p[0].equals("f")) {
                String srg = tsrgField.get(curObf + " " + obfName);
                d.put(d.fields, interName, srg != null ? srg : obfName);
            }
        }
        return d;
    }

    private void put(Map<String, String> map, String key, String value) {
        String prev = map.putIfAbsent(key, value);
        if (prev != null && !prev.equals(value)) {
            conflicts++;
        }
    }
}
