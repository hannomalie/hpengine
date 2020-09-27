package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope

class SceneManager(val engineContext: EngineContext, initialScene: Scene): Manager {
    val addResourceContext: AddResourceContext = engineContext.backend.addResourceContext
    var scene: Scene = initialScene
        set(value) {
            beforeSetScene(field, value)
            addResourceContext.locked {
                field = value
            }
            afterSetScene(field, value)
        }

    fun addAll(entities: List<Entity>) {
        addResourceContext.locked {
            with(scene) { addAll(entities) }
        }
    }
    fun add(entity: Entity) = addAll(listOf(entity))

    fun addComponent(selection: Entity, component: Component) {
        addResourceContext.locked {
            with(scene) {
                addComponent(selection, component)
            }
            onComponentAdded(component)
        }
    }

    override fun onComponentAdded(component: Component) {
        scene.addComponent(component.entity, component)
    }

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {

        with(scene) {
            update(scene, deltaSeconds)
        }
    }

    override fun afterSetScene(lastScene: Scene, currentScene: Scene) {
        engineContext.afterSetScene(lastScene, currentScene)
    }

    override fun beforeSetScene(currentScene: Scene, nextScene: Scene) {
        scene.clear()
        nextScene.entitySystems.gatherEntities(nextScene)
    }

}