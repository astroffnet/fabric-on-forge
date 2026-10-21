package dev.fabricforge.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remaps Fabric (intermediary) bytecode, annotation strings and mixin refmaps to Forge runtime names. */
public final class FabricRemapper extends Remapper {
    private static final Pattern NAMES = Pattern.compile(
        "net[/.]minecraft[/.]class_\\d+(?:\\$\\w+)*|\\bmethod_\\d+\\b|\\bfield_\\d+\\b");

    private final MappingData data;

    public FabricRemapper(MappingData data) {
        this.data = data;
    }

    @Override
    public String map(String internalName) {
        String m = data.mapClass(internalName);
        if (m != null) {
            return m;
        }
        // anonymous / unnamed inner classes: map the outer class, keep the suffix
        if (internalName.startsWith("net/minecraft/class_")) {
            int i = internalName.lastIndexOf('$');
            if (i > 0) {
                String outer = internalName.substring(0, i);
                String mappedOuter = map(outer);
                if (!mappedOuter.equals(outer)) {
                    return mappedOuter + internalName.substring(i);
                }
            }
        }
        return internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return methodName(name);
    }

    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        return methodName(name);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (name.startsWith("field_")) {
            String m = data.mapField(name);
            if (m != null) {
                return m;
            }
        }
        return name;
    }

    private String methodName(String name) {
        if (name.startsWith("method_")) {
            String m = data.mapMethod(name);
            if (m != null) {
                return m;
            }
        }
        return name;
    }

    /**
     * Replaces every intermediary class/method/field token inside an arbitrary string
     * (annotation values, mixin refmap entries, descriptors, dotted class names).
     */
    public String mapNames(String text) {
        if (text.indexOf("class_") < 0 && text.indexOf("method_") < 0 && text.indexOf("field_") < 0) {
            return text;
        }
        Matcher m = NAMES.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String t = m.group();
            String r;
            if (t.startsWith("net")) {
                boolean dotted = t.indexOf('.') >= 0;
                String mapped = map(t.replace('.', '/'));
                r = dotted ? mapped.replace('/', '.') : mapped;
            } else if (t.startsWith("method_")) {
                r = methodName(t);
            } else {
                r = mapFieldName(null, t, null);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public byte[] remap(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new AnnotationNameMapper(new ClassRemapper(writer, this), this), 0);
        return writer.toByteArray();
    }
}
