package org.example.runtime;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public final class G17Proof {
    private G17Proof() {}
    public static void mark(String type, String value) {
        Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (!userHome.equals(Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g17-loader-12213219410446414803\\sandbox-home"))) {
            throw new IllegalStateException("Unexpected game home: " + userHome);
        }
        try {
            Path folder = Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g17-loader-12213219410446414803\\runtime-proof");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("b4b904ce-55c2-4d3f-b2b2-797f96fd6d76." + type), value, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not record isolated Loader proof", error);
        }
    }
}
