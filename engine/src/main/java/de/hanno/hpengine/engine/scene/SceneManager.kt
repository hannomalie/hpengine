package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.manager.Manager

class SceneManager(val addResourceContext: AddResourceContext): Manager {
    private var _scene: Scene? = null
    var scene: Scene
        get() {
            return _scene!!
        }
        set(value) {
            addResourceContext.launch {
                val oldScene = _scene
                _scene = value
                afterSetScene(oldScene, _scene!!)
                oldScene?.closeScope()
            }
        }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        scene.update(scene, deltaSeconds)
    }

    override fun beforeSetScene(nextScene: Scene) {
////        currentScene.clear() // TODO: Where to close old scopes?
//        nextScene.entitySystems.forEach { it.gatherEntities(nextScene) }
//        nextScene.extensions.forEach { it.run { nextScene.decorate() } }
//
//        nextScene.componentSystems.forEach {
//            it.onEntityAdded(nextScene.getEntities())
//        }
////         TODO: Why does this not work?
//        nextScene.managers.forEach { it.beforeSetScene(nextScene) }
    }

    override fun afterSetScene(lastScene: Scene?, currentScene: Scene) {
        currentScene.scope.getAll<DeferredRenderExtension<*>>().forEach { it.afterSetScene(currentScene) }
        currentScene.managers.filterNot { it is SceneManager }.forEach { it.afterSetScene(lastScene, currentScene) }
    }
}