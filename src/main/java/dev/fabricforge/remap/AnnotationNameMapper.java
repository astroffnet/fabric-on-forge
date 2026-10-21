package dev.fabricforge.remap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites intermediary names inside annotation STRING values
 * (e.g. @Inject(method = "method_1234"), @Mixin(targets = "net.minecraft.class_1"), @Accessor("field_5")).
 * Class literals are handled by ClassRemapper itself.
 */
final class AnnotationNameMapper extends ClassVisitor {
    private final FabricRemapper remapper;

    AnnotationNameMapper(ClassVisitor next, FabricRemapper remapper) {
        super(Opcodes.ASM9, next);
        this.remapper = remapper;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return wrap(super.visitAnnotation(descriptor, visible));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
        if (fv == null) {
            return null;
        }
        return new FieldVisitor(Opcodes.ASM9, fv) {
            @Override
            public AnnotationVisitor visitAnnotation(String d, boolean v) {
                return wrap(super.visitAnnotation(d, v));
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (mv == null) {
            return null;
        }
        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public AnnotationVisitor visitAnnotation(String d, boolean v) {
                return wrap(super.visitAnnotation(d, v));
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String d, boolean v) {
                return wrap(super.visitParameterAnnotation(parameter, d, v));
            }
        };
    }

    private AnnotationVisitor wrap(AnnotationVisitor av) {
        if (av == null) {
            return null;
        }
        return new AnnotationVisitor(Opcodes.ASM9, av) {
            @Override
            public void visit(String name, Object value) {
                super.visit(name, value instanceof String s ? remapper.mapNames(s) : value);
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return wrap(super.visitArray(name));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                return wrap(super.visitAnnotation(name, descriptor));
            }
        };
    }
}
