package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

class ProbeSystem(sceneImpl: Scene): SimpleEntitySystem(sceneImpl, emptyList()) {
    override fun CoroutineScope.update(deltaSeconds: Float) { }

}
