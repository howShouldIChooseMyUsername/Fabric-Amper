package org.example.runtime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
public final class G17Main implements ModInitializer {
    @Override public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("amper_rt_g17")) {
            throw new IllegalStateException("Fabric Loader did not discover G17");
        }
        if (!((Object) ItemStack.EMPTY instanceof G17Injected)) {
            throw new IllegalStateException("Class Tweaker failed to implement G17Injected");
        }
        if (!"G17_DEFAULT_OK".equals(ItemStack.EMPTY.amper_rt_g17$ping())) {
            throw new IllegalStateException("G17 default method not available in Loader");
        }
        G17Proof.mark("main", "b4b904ce-55c2-4d3f-b2b2-797f96fd6d76:loader-main-interface-default-ok");
    }
}
