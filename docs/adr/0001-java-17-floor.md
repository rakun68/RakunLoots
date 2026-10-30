# Java 17, Minecraft 1.18 through Paper 26.2

The public plugin is one JAR. Its entry classes are Java 17 bytecode, so it loads on Paper and Spigot from 1.18 through Paper 26.2. 1.16 and 1.17 are out. A JAR built for Java 25 would not load on 1.18.

## Considered options

- Java 8 entry classes, so the same JAR also loads on 1.16. Rejected. The core would be stuck on Java 8, and current item-source libraries sit past that line.
- One JAR compiled for Paper 26.2. Rejected. Java 25 bytecode does not load on 1.18.

## Consequences

Newer API calls live in adapters that load only on servers new enough to run them. The core stays on the 1.18 surface. An adapter may target a higher Java level than 17 when every server that loads it runs that level, such as the FancyHolograms provider and newer packet adapters (ADR 0012).
