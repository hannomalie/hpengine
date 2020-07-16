package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.SceneImpl
import kotlinx.coroutines.CoroutineScope

class ProbeSystem(sceneImpl: SceneImpl): SimpleEntitySystem(sceneImpl, emptyList()) {
    override fun CoroutineScope.update(deltaSeconds: Float) { }

}
