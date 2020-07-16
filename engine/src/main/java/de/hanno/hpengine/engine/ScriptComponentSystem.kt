package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileMonitor
import de.hanno.hpengine.util.ressources.StringBasedCodeSource

class ScriptComponentSystem : SimpleComponentSystem<ScriptComponent>(ScriptComponent::class.java) {

    override fun addComponent(component: ScriptComponent) = with(component) {
        val codeSource = codeSource
        if (codeSource is FileBasedCodeSource) {
            FileMonitor.addOnFileChangeListener(codeSource.file) {
                component.reload()
            }
        }
        addComponentImpl(component)
    }
}