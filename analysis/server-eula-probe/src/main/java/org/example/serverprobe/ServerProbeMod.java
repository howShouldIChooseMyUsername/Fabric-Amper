package org.example.serverprobe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ServerProbeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("fabric_amper_server_probe")) {
            throw new IllegalStateException("The server did not discover Fabric Amper metadata");
        }
        System.out.println("FABRIC_AMPER_SERVER_LOADER_INITIALIZER=SUCCESS");
    }
}
