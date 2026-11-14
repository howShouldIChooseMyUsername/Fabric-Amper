package org.example.runtime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.item.ItemStack;
import java.util.concurrent.atomic.AtomicBoolean;
public final class G17Client implements ClientModInitializer {
    private static final AtomicBoolean FIRST_TICK = new AtomicBoolean();
    @Override public void onInitializeClient() {
        G17Proof.mark("client", "b4b904ce-55c2-4d3f-b2b2-797f96fd6d76:loader-client-initialized");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FIRST_TICK.compareAndSet(false, true)) {
                if (!((Object) ItemStack.EMPTY instanceof G17Injected) ||
                        !"G17_DEFAULT_OK".equals(ItemStack.EMPTY.amper_rt_g17$ping())) {
                    throw new IllegalStateException("Interface injection missing during actual game tick");
                }
                G17Proof.mark("tick", "b4b904ce-55c2-4d3f-b2b2-797f96fd6d76:real-client-tick-interface-default-ok");
            }
        });
    }
}
