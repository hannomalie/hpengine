package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.scene.Scene

class ProbeSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, emptyList()) {
    override fun update(deltaSeconds: Float) { }

}
