package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope

class ProbeSystem : SimpleEntitySystem(emptyList()) {
    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) { }

}
