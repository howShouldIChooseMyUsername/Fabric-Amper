package org.example;

import net.fabricmc.api.ModInitializer;

public final class ProbeMod implements ModInitializer {
    @Override public void onInitialize() {
        System.out.println("Fabric Amper shared publishing probe loaded");
    }
}
