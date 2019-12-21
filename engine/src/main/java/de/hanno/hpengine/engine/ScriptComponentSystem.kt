package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.UpdateLock
import de.hanno.hpengine.util.ressources.FileMonitor

class ScriptComponentSystem : SimpleComponentSystem<ScriptComponent>(ScriptComponent::class.java) {

    override fun UpdateLock.addComponent(component: ScriptComponent) = with(component) {
        if (codeSource.isFileBased) {
            FileMonitor.addOnFileChangeListener(codeSource.file) {
                component.reload()
            }
        }
        addComponentImpl(component)
    }
}