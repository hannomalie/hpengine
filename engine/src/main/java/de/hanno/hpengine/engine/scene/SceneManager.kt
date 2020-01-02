package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope

class SceneManager(val managerContext: ManagerContext<OpenGl>): Manager {

    var scene: Scene = SimpleScene("InitialScene", managerContext)

    fun addAll(entities: List<Entity>) {
        managerContext.addResourceContext.locked {
            addAll(entities)
        }
    }
    private fun UpdateLock.addAll(entities: List<Entity>) {
        with(scene) { addAll(entities) }
        managerContext.managers.managers.values.forEach {
            with(it) { onEntityAdded(entities) }
        }
    }
    fun UpdateLock.add(entity: Entity) = addAll(listOf(entity))

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val newDrawCycle = managerContext.renderManager.drawCycle.get()
        scene.currentCycle = newDrawCycle
        with(scene) {
            update(deltaSeconds)
        }
    }

    override fun CoroutineScope.afterUpdate(deltaSeconds: Float) {
        with(scene) {
            afterUpdate(deltaSeconds)
        }
    }

    override fun beforeSetScene(nextScene: Scene) {
        scene.environmentProbeManager.clearProbes()
        managerContext.physicsManager.clearWorld()
        managerContext.renderManager.clear()
        scene.clear()
        nextScene.entitySystems.gatherEntities()
    }

}