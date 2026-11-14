package org.example.runtime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
public final class HostMain implements ModInitializer {
    @Override public void onInitialize() {
        boolean producer = FabricLoader.getInstance().isModLoaded("amper_rt_g10_embedded");
        boolean nested = FabricLoader.getInstance().isModLoaded("org_apache_commons_commons-collections4");
        boolean classFound;
        try {
            Class.forName("org.apache.commons.collections4.bag.HashBag", false,
                HostMain.class.getClassLoader());
            classFound = true;
        } catch (ClassNotFoundException missing) {
            classFound = false;
        }
        if (producer != false || nested != false ||
                classFound != false) {
            throw new IllegalStateException("Unexpected Loader JIJ visibility: " +
                producer + "/" + nested + "/" + classFound);
        }
        ProofWriter.mark("host", "279760b7-4ad0-4a1f-a53b-0a142610d759:loader-saw-absent-external-and-nested-library");
    }
}
