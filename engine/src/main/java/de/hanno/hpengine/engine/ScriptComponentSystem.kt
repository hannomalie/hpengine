package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.util.ressources.FileMonitor

class ScriptComponentSystem(val engine: Engine<*>): SimpleComponentSystem<ScriptComponent>(ScriptComponent::class.java, factory = { TODO("not implemented")}) {

    override fun addComponent(component: ScriptComponent) = with(component) {
        if (codeSource.isFileBased) {
            FileMonitor.addOnFileChangeListener(codeSource.file) {
                component.reload()
            }
        }
        super.addComponent(component)
    }
}