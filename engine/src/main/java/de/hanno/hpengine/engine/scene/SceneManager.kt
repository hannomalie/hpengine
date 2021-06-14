package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager

class SceneManager(val engineContext: EngineContext, initialScene: Scene): Manager {
    val addResourceContext: AddResourceContext = engineContext.backend.addResourceContext
    var scene: Scene = initialScene
        set(value) = addResourceContext.locked {
            val oldScene = field
            beforeSetScene(currentScene = oldScene, nextScene = value)
            field = value
            afterSetScene(oldScene, value)
        }

    init {
        initialScene.entitySystems.gatherEntities(initialScene)
        engineContext.extensions.forEach {
            it.run { initialScene.decorate() }
        }
    }
    fun addAll(entities: List<Entity>) = addResourceContext.locked {
        scene.addAll(entities)
    }
    fun add(entity: Entity) = addAll(listOf(entity))

    override fun onComponentAdded(component: Component) {
        scene.addComponent(component.entity, component)
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        scene.update(scene, deltaSeconds)
    }

    override fun beforeSetScene(currentScene: Scene, nextScene: Scene) {
        currentScene.clear()
        nextScene.entitySystems.gatherEntities(nextScene)
        engineContext.extensions.forEach {
            it.run { nextScene.decorate() }
        }
        engineContext.extensions.forEach {
            it.componentSystem?.onEntityAdded(nextScene.getEntities())
            it.manager?.beforeSetScene(currentScene, nextScene)
        }
    }

    override fun afterSetScene(lastScene: Scene, currentScene: Scene) {
        engineContext.extensions.forEach { it.manager?.afterSetScene(lastScene, currentScene) }
    }
}