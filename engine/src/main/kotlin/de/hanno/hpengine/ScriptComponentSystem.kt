package de.hanno.hpengine

import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.KotlinComponent
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor

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
