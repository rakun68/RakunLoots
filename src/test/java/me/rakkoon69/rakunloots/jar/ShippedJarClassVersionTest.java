package me.rakkoon69.rakunloots.jar;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin jar as it ships. Paper's plugin remapper on 1.20.6 to 1.21.5 reads every class in it and cannot read a
 * class newer than Java 21, so the 26.x packet adapters and the FancyHolograms provider are compiled to Java 21
 * bytecode on the JDK 25 toolchain (ADR 0017).
 */
class ShippedJarClassVersionTest {

    private static final int JAVA_21 = 65;

    /**
     * Acceptance: no class in the shipped jar has a major version above 65.
     */
    @Test
    void noClassInTheShippedJarIsNewerThanJava21() throws IOException {
        Path path = Path.of(System.getProperty("rakunloots.shadowJar"));
        assertTrue(Files.isRegularFile(path), path.toString());
        List<String> tooNew = new ArrayList<>();
        int classes = 0;
        try (JarFile jar = new JarFile(path.toFile())) {
            for (JarEntry entry : jar.stream().toList()) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                classes++;
                int major = major(jar, entry);
                if (major > JAVA_21) {
                    tooNew.add(entry.getName() + " is major version " + major);
                }
            }
        }

        assertTrue(classes > 0, "no classes in " + path);
        assertEquals(List.of(), tooNew);
    }

    private static int major(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] header = input.readNBytes(8);
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }
}
