package org.example.runtime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
public final class ProofWriter {
    private ProofWriter() {}
    public static void mark(String name, String evidence) {
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (!userHome.equals(Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g15-loader-9968972708243135777\\sandbox-home"))) {
            throw new IllegalStateException("Runtime must not use the user's Minecraft home: " + userHome);
        }
        try {
            Path folder = Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g15-loader-9968972708243135777\\runtime-proof");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("8b506823-198f-4b0c-b77c-68741a402f9c." + name), evidence, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
