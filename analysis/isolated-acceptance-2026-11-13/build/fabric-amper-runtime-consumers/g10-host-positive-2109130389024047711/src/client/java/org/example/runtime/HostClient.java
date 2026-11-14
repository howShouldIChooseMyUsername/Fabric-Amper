package org.example.runtime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.atomic.AtomicBoolean;
public final class HostClient implements ClientModInitializer {
    private static final AtomicBoolean FIRST = new AtomicBoolean();
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FIRST.compareAndSet(false, true)) {
                ProofWriter.mark("tick", "e39aaa0e-7ea2-42ff-a560-cb39571feaf8:host-client-tick");
            }
        });
    }
}
