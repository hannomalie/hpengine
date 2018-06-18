package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.SimpleScene

class ProbeSystem(engine: Engine, simpleScene: SimpleScene): SimpleEntitySystem(engine, simpleScene, emptyList()) {
    override fun update(deltaSeconds: Float) { }

}
