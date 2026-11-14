package org.example.runtime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.collections4.bag.HashBag;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class EmbeddedEntrypoint implements ModInitializer {
    @Override public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("amper_rt_g10_embedded")) {
            throw new IllegalStateException("External JAR not discovered by Loader");
        }
        HashBag<String> bag = new HashBag<>();
        bag.add("loaded");
        if (bag.getCount("loaded") != 1) throw new IllegalStateException("Nested HashBag failure");
        if (!Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
                .equals(Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g10-host-positive-2109130389024047711\\sandbox-home"))) {
            throw new IllegalStateException("External mod escaped the isolated home");
        }
        try {
            Path marker = Path.of("C:\\Users\\user\\Documents\\vbc\\Fabric Amper\\analysis\\isolated-acceptance-2026-11-13\\build\\fabric-amper-runtime-consumers\\g10-host-positive-2109130389024047711\\runtime-proof\\e39aaa0e-7ea2-42ff-a560-cb39571feaf8.embedded");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "e39aaa0e-7ea2-42ff-a560-cb39571feaf8:embedded-library-invoked",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
