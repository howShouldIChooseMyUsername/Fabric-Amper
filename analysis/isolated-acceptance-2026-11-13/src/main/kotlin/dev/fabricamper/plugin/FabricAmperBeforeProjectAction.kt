package dev.fabricamper.plugin

import org.gradle.api.IsolatedAction
import org.gradle.api.Project

/** Applied to each project by the Settings lifecycle hook, but only acts on the root. */
class FabricAmperBeforeProjectAction : IsolatedAction<Project> {
    override fun execute(target: Project) {
        if (target.path == ":") {
            target.pluginManager.apply(FabricAmperProjectPlugin::class.java)
        }
    }
}
