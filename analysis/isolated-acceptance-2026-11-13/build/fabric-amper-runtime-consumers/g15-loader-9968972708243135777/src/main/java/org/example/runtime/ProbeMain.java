package org.example.runtime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
public final class ProbeMain implements ModInitializer {
    @Override public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("amper_rt_g15")) {
            throw new IllegalStateException("Loader did not discover amper_rt_g15");
        }
        ProofWriter.mark("main", "8b506823-198f-4b0c-b77c-68741a402f9c:loader-main");
    }
}
