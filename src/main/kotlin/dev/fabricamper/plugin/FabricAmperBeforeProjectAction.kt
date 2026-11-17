package dev.fabricamper.plugin

import org.gradle.api.IsolatedAction
import org.gradle.api.Project

/** Apply only the loader projects selected by the shared root YAML. */
class FabricAmperBeforeProjectAction : IsolatedAction<Project> {
    override fun execute(target: Project) {
        if (target.path == ":") {
            target.pluginManager.apply(FabricAmperProjectPlugin::class.java)
        } else if (target.path in setOf(":common", ":fabric", ":neoforge")) {
            // The project plugin checks the selected targets before applying
            // either backend. No root/child Project object crosses this
            // IsolatedAction boundary.
            target.pluginManager.apply(FabricAmperProjectPlugin::class.java)
        }
    }
}
