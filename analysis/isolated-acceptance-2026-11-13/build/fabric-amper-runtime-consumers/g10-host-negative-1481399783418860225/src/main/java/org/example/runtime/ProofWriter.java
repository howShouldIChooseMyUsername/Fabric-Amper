package org.example.runtime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
public final class ProofWriter {
    private ProofWriter() {}
    public static void mark(String name, String evidence) {
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (!userHome.equals(Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g10-host-negative-1481399783418860225\\sandbox-home"))) {
            throw new IllegalStateException("Runtime must not use the user's Minecraft home: " + userHome);
        }
        try {
            Path folder = Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g10-host-negative-1481399783418860225\\runtime-proof");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("279760b7-4ad0-4a1f-a53b-0a142610d759." + name), evidence, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
