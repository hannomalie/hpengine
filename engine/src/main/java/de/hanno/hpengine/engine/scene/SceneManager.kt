package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.addResourceContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope

class SceneManager(val managerContext: ManagerContext): Manager {

    var scene: Scene = Scene("InitialScene", managerContext.engineContext)

    fun addAll(entities: List<Entity>) {
        managerContext.engineContext.addResourceContext.locked {
            with(scene) { addAll(entities) }
            managerContext.managers.managers.values.forEach {
                with(it) { onEntityAdded(entities) }
            }
        }
    }
    fun add(entity: Entity) = addAll(listOf(entity))

    fun addComponent(selection: Entity, component: Component) {
        managerContext.engineContext.addResourceContext.locked {
            with(scene) {
                addComponent(selection, component)
            }
            onComponentAdded(component)
        }
    }

    override fun onComponentAdded(component: Component) {
        managerContext.managers.managers.values.filter { it !is SceneManager }.forEach {
            with(it) { onComponentAdded(component) }
        }
    }

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
        val newDrawCycle = managerContext.renderManager.updateCycle.get()
        scene.currentCycle = newDrawCycle
        with(scene) {
            update(scene, deltaSeconds)
        }
    }

    override fun beforeSetScene(nextScene: Scene) {
        scene.clear()
        nextScene.entitySystems.gatherEntities(nextScene)
    }

}