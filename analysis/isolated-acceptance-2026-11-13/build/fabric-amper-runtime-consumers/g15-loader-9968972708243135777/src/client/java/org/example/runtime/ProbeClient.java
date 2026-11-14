package org.example.runtime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ProbeClient implements ClientModInitializer {
    private static final AtomicBoolean FIRST_TICK = new AtomicBoolean();
    @Override public void onInitializeClient() {
        ProofWriter.mark("client", "8b506823-198f-4b0c-b77c-68741a402f9c:loader-client");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (FIRST_TICK.compareAndSet(false, true)) {
                ProofWriter.mark("tick", "8b506823-198f-4b0c-b77c-68741a402f9c:actual-client-tick");
            }
        });
    }
}
