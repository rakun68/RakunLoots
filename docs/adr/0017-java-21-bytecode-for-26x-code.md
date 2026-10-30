# The 26.x code ships as Java 21 bytecode

Paper 1.20.5 and later remaps every plugin jar with ASM when it loads. On Paper 1.20.6, 1.21.1, and 1.21.4, that ASM cannot read Java 25 class files, so the plugin fails to load there because of the `v26_1` and `v26_2` packet adapters and the FancyHolograms provider, which were compiled for Java 25. Paper 1.21.8 and later read them. Their source is now compiled with `-source 21 -target 21` on JDK 25, against the 26.x server and FancyHolograms jars. A Java 21 class file loads on a Java 25 server, and javac reads Java 25 class files on its classpath. That source may not use a Java 22 or later language feature. A build test fails when any shipped class is newer than Java 21.

## Considered options

- A nested jar for the Java 25 classes, loaded by name and never remapped. Rejected unless the Java 21 build fails. It adds a class loader and a second jar layout.
- The `paperweight-mappings-namespace: mojang` manifest attribute, which skips the remapper. Rejected. The reobfuscated adapters up to 1.21.11 need the remapper on Paper 1.20.5 and later.

## Consequences

ADR 0001 and phase 7c said each adapter targets its server's Java level. The 26.x adapters are the exception: they target Java 21. A future server whose API needs a newer language feature in that source reopens this.
