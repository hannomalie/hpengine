package de.hanno.hpengine.engine

import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.engine.component.artemis.KotlinComponent
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileMonitor

@All(KotlinComponent::class)
class ScriptComponentSystem : BaseEntitySystem(){

    lateinit var kotlinComponentComponentMapper: ComponentMapper<KotlinComponent>
    override fun processSystem() {
        TODO("Not yet implemented")
    }

    override fun inserted(entityId: Int) {
        val codeSource = kotlinComponentComponentMapper[entityId].codeSource
        if (codeSource is FileBasedCodeSource) {
            FileMonitor.addOnFileChangeListener(codeSource.file) {
                codeSource.reload()
            }
        }
    }
}
