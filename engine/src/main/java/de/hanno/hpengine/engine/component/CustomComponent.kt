package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

interface CustomComponent: Component {

    companion object {
        val identifier = CustomComponent::class.java.simpleName

        fun Entity.customComponent(update: CoroutineScope.(Scene, Float) -> Unit) {
            addComponent(object: CustomComponent {
                override val entity = this@customComponent
                override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
                    update(scene, deltaSeconds)
                }
            })
        }
    }
}

class CustomComponentSystem : SimpleComponentSystem<CustomComponent>(CustomComponent::class.java)
