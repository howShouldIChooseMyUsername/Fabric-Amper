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
        if (producer != true || nested != true ||
                classFound != true) {
            throw new IllegalStateException("Unexpected Loader JIJ visibility: " +
                producer + "/" + nested + "/" + classFound);
        }
        ProofWriter.mark("host", "e39aaa0e-7ea2-42ff-a560-cb39571feaf8:loader-saw-external-and-nested-library");
    }
}
