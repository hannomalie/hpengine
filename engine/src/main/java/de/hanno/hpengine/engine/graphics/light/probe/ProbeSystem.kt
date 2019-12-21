package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.SimpleScene
import kotlinx.coroutines.CoroutineScope

class ProbeSystem(simpleScene: SimpleScene): SimpleEntitySystem(simpleScene, emptyList()) {
    override fun CoroutineScope.update(deltaSeconds: Float) { }

}
