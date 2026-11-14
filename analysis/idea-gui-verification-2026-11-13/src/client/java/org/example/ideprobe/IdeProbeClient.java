package org.example.ideprobe;

import net.fabricmc.api.ClientModInitializer;

public final class IdeProbeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Fabric Amper IDE client source-set recognized");
    }
}
