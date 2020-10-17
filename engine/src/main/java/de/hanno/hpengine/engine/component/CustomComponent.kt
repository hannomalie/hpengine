package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface CustomComponent: Component {

    companion object {
        val identifier = CustomComponent::class.java.simpleName

        fun Entity.customComponent(update: suspend (Scene, Float) -> Unit) {
            addComponent(object: CustomComponent {
                override val entity = this@customComponent
                override suspend fun update(scene: Scene, deltaSeconds: Float) {
                    update.invoke(scene, deltaSeconds)
                }
            })
        }
    }
}

class CustomComponentSystem : SimpleComponentSystem<CustomComponent>(CustomComponent::class.java)
