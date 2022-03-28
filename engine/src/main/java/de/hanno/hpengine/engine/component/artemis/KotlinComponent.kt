package de.hanno.hpengine.engine.component.artemis

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.swirtz.ktsrunner.objectloader.KtsObjectLoader

class KotlinComponent: Component() {
    lateinit var codeSource: CodeSource
}

@All(KotlinComponent::class)
class KotlinComponentSystem: BaseEntitySystem() {
    lateinit var kotlinComponentMapper: ComponentMapper<KotlinComponent>

    private val objectLoader = KtsObjectLoader()
    private val instanceCache = mutableMapOf<Int, Updatable>()

    override fun inserted(entityId: Int) {
        val kotlinComponent = kotlinComponentMapper[entityId]
        val codeSource = kotlinComponent.codeSource

        codeSource.load()
        objectLoader.engine.eval(codeSource.source)

        (codeSource as FileBasedCodeSource?)?.let {
            val instance = objectLoader.engine.eval("${codeSource.filename}()")!!
            if (instance is Updatable) {
                instanceCache[entityId] = instance
            }
        }
    }

    override fun removed(entityId: Int) {
        instanceCache.remove(entityId)
    }

    override fun processSystem() {
        instanceCache.values.forEach { it.update(world.delta) }
    }

}