package me.rakkoon69.rakunloots.hologram.packet;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packet adapter as the plugin jar ships it, checked the same way for every revision. The build names the
 * revision, its Java level and mappings, the shadow jar, and the classpath of the server as it runs: Spigot's own
 * server for spigot mapped adapters, whose names differ from the Mojang names the adapter is written in.
 */
class ShippedAdapterTest {

    private static final String REVISION = System.getProperty("rakunloots.revision");
    private static final int BYTECODE_LEVEL = Integer.parseInt(System.getProperty("rakunloots.bytecodeLevel"));
    private static final String MAPPINGS = System.getProperty("rakunloots.mappings");
    private static final String PACKAGE = "me/rakkoon69/rakunloots/hologram/packet/" + REVISION + "/";
    private static final int CLASS_VERSION_OFFSET = 44;
    private static final int JAVA_17 = 61;

    /**
     * Acceptance: the shadow jar contains the adapter at its revision's Java level, beside the Java 17 core.
     */
    @Test
    void theShadowJarContainsTheAdapterAtItsJavaLevel() throws IOException {
        try (JarFile jar = new JarFile(shadowJar().toFile())) {
            List<String> adapterClasses = adapterClasses(jar);

            assertTrue(adapterClasses.contains(PACKAGE + "NmsPacketAdapter.class"), adapterClasses.toString());
            for (String entry : adapterClasses) {
                assertEquals(BYTECODE_LEVEL + CLASS_VERSION_OFFSET, major(jar, entry), entry);
            }
            assertEquals(JAVA_17, major(jar, "me/rakkoon69/rakunloots/hologram/packet/PacketAdapters.class"));
            assertEquals(JAVA_17, major(jar, "me/rakkoon69/rakunloots/RakunLootsPlugin.class"));
        }
    }

    /**
     * Acceptance: the shipped adapter is the remapped one. Every server class, field, and method it names exists under
     * that name in the server as it runs.
     */
    @Test
    void everyServerNameTheShippedAdapterUsesExistsInTheRunningServer() throws IOException {
        Set<Reference> references = new TreeSet<>();
        try (JarFile jar = new JarFile(shadowJar().toFile())) {
            for (String entry : adapterClasses(jar)) {
                try (InputStream input = jar.getInputStream(jar.getJarEntry(entry))) {
                    references.addAll(serverReferences(input));
                }
            }
        }

        assertFalse(references.isEmpty(), "the shipped adapter names no server class");
        try (URLClassLoader server = runningServer()) {
            assertEquals(List.of(), missing(references, server));
        }
    }

    /**
     * The check above has teeth: the adapter as compiled, in Mojang names, names classes or members that a spigot
     * mapped server does not have, so shipping it unremapped would fail on the server.
     */
    @Test
    void theAdapterAsCompiledDoesNotLinkAgainstASpigotMappedServer() throws IOException {
        if (!MAPPINGS.equals("spigot")) {
            return;
        }
        Set<Reference> references = new TreeSet<>();
        try (InputStream input = compiledAdapter()) {
            references.addAll(serverReferences(input));
        }

        try (URLClassLoader server = runningServer()) {
            assertFalse(missing(references, server).isEmpty(), references.toString());
        }
    }

    /**
     * A mojang mapped adapter, for 26.x, whose servers all run Mojang's names, ships as compiled: each of its classes
     * in the shadow jar is the compiled class byte for byte, with no remapping.
     */
    @Test
    void aMojangMappedAdapterShipsAsCompiled() throws IOException {
        if (!MAPPINGS.equals("mojang")) {
            return;
        }
        try (JarFile jar = new JarFile(shadowJar().toFile())) {
            List<String> adapterClasses = adapterClasses(jar);

            assertFalse(adapterClasses.isEmpty(), "no " + PACKAGE + " classes in the shadow jar");
            for (String entry : adapterClasses) {
                try (InputStream shipped = jar.getInputStream(jar.getJarEntry(entry));
                     InputStream compiled = ShippedAdapterTest.class.getClassLoader().getResourceAsStream(entry)) {
                    assertTrue(compiled != null, entry);
                    assertTrue(Arrays.equals(compiled.readAllBytes(), shipped.readAllBytes()), entry);
                }
            }
        }
    }

    private static Path shadowJar() {
        Path jar = Path.of(System.getProperty("rakunloots.shadowJar"));
        assertTrue(Files.isRegularFile(jar), jar.toString());
        return jar;
    }

    private static List<String> adapterClasses(JarFile jar) {
        return jar.stream().map(JarEntry::getName)
                .filter(name -> name.startsWith(PACKAGE) && name.endsWith(".class"))
                .toList();
    }

    private static InputStream compiledAdapter() {
        String entry = PACKAGE + "NmsPacketAdapter.class";
        InputStream input = ShippedAdapterTest.class.getClassLoader().getResourceAsStream(entry);
        assertTrue(input != null, entry);
        return input;
    }

    private static int major(JarFile jar, String entry) throws IOException {
        try (InputStream input = jar.getInputStream(jar.getJarEntry(entry))) {
            byte[] header = input.readNBytes(8);
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }

    private static URLClassLoader runningServer() {
        String path = System.getProperty("rakunloots.runtimeServer");
        URL[] urls = Arrays.stream(path.split(File.pathSeparator)).map(ShippedAdapterTest::url).toArray(URL[]::new);
        // The platform loader is the parent, so none of the Mojang-mapped test classpath shows through.
        return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }

    private static URL url(String file) {
        try {
            return new File(file).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A class, field, or method of the server that an adapter class names. A class has no name or descriptor; a
     * constructor is the method {@code <init>}.
     */
    record Reference(String owner, String name, String descriptor, boolean field) implements Comparable<Reference> {

        static Reference type(String internalName) {
            return new Reference(internalName, "", "", false);
        }

        @Override
        public int compareTo(Reference other) {
            return toString().compareTo(other.toString());
        }

        @Override
        public String toString() {
            return name.isEmpty() ? owner : owner + (field ? "." : "#") + name + descriptor;
        }
    }

    private static boolean serverClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("org/bukkit/craftbukkit/")
                || internalName.startsWith("com/mojang/");
    }

    /**
     * Every server class the class file names in a descriptor, an instruction, or a constant, and every server field
     * and method it uses.
     */
    static Set<Reference> serverReferences(InputStream classFile) throws IOException {
        Set<Reference> references = new HashSet<>();
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                addInternal(references, superName);
                for (String type : interfaces) {
                    addInternal(references, type);
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                           Object value) {
                addType(references, Type.getType(descriptor));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                addType(references, Type.getMethodType(descriptor));
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        addType(references, Type.getObjectType(type));
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        addMember(references, owner, name, descriptor, true);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                boolean isInterface) {
                        addMember(references, owner, name, descriptor, false);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap,
                                                       Object... arguments) {
                        addType(references, Type.getMethodType(descriptor));
                        for (Object argument : arguments) {
                            addConstant(references, argument);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        addConstant(references, value);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                        addType(references, Type.getType(descriptor));
                    }

                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature,
                                                   org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                                   int index) {
                        addType(references, Type.getType(descriptor));
                    }
                };
            }
        }, 0);
        return references;
    }

    private static void addConstant(Set<Reference> references, Object constant) {
        if (constant instanceof Type type) {
            addType(references, type);
        } else if (constant instanceof Handle handle) {
            addMember(references, handle.getOwner(), handle.getName(), handle.getDesc(),
                    handle.getTag() <= Opcodes.H_PUTSTATIC);
        }
    }

    private static void addMember(Set<Reference> references, String owner, String name, String descriptor,
                                  boolean field) {
        Type ownerType = Type.getObjectType(owner);
        addType(references, ownerType);
        addType(references, field ? Type.getType(descriptor) : Type.getMethodType(descriptor));
        if (ownerType.getSort() == Type.OBJECT && serverClass(owner)) {
            references.add(new Reference(owner, name, descriptor, field));
        }
    }

    private static void addType(Set<Reference> references, Type type) {
        switch (type.getSort()) {
            case Type.ARRAY -> addType(references, type.getElementType());
            case Type.OBJECT -> addInternal(references, type.getInternalName());
            case Type.METHOD -> {
                addType(references, type.getReturnType());
                for (Type argument : type.getArgumentTypes()) {
                    addType(references, argument);
                }
            }
            default -> {
            }
        }
    }

    private static void addInternal(Set<Reference> references, String internalName) {
        if (internalName != null && serverClass(internalName)) {
            references.add(Reference.type(internalName));
        }
    }

    /**
     * The references the server lacks: a class that does not load, or a field or method that neither the class nor
     * any type above it declares with that name and descriptor.
     */
    static List<String> missing(Set<Reference> references, ClassLoader server) {
        List<String> missing = new ArrayList<>();
        for (Reference reference : references) {
            try {
                Class<?> owner = Class.forName(reference.owner().replace('/', '.'), false, server);
                if (!reference.name().isEmpty() && !declares(owner, reference)) {
                    missing.add(reference.toString());
                }
            } catch (ClassNotFoundException | LinkageError e) {
                missing.add(reference + " (" + e + ")");
            }
        }
        return missing;
    }

    private static boolean declares(Class<?> owner, Reference reference) {
        if (reference.name().equals("<init>")) {
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                if (Type.getConstructorDescriptor(constructor).equals(reference.descriptor())) {
                    return true;
                }
            }
            return false;
        }
        Deque<Class<?>> types = new ArrayDeque<>(List.of(owner));
        Set<Class<?>> seen = new HashSet<>();
        while (!types.isEmpty()) {
            Class<?> type = types.pop();
            if (!seen.add(type)) {
                continue;
            }
            if (reference.field()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getName().equals(reference.name())
                            && Type.getDescriptor(field.getType()).equals(reference.descriptor())) {
                        return true;
                    }
                }
            } else {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals(reference.name())
                            && Type.getMethodDescriptor(method).equals(reference.descriptor())) {
                        return true;
                    }
                }
            }
            if (type.getSuperclass() != null) {
                types.add(type.getSuperclass());
            }
            types.addAll(List.of(type.getInterfaces()));
        }
        return false;
    }
}
