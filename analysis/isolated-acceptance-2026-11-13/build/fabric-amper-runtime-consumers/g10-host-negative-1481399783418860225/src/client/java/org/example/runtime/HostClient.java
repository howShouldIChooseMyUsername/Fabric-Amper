package org.example.runtime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.atomic.AtomicBoolean;
public final class HostClient implements ClientModInitializer {
    private static final AtomicBoolean FIRST = new AtomicBoolean();
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FIRST.compareAndSet(false, true)) {
                ProofWriter.mark("tick", "279760b7-4ad0-4a1f-a53b-0a142610d759:host-client-tick");
            }
        });
    }
}
