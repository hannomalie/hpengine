package de.hanno.hpengine.engine.graphics.light.probe

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateConsumer
import de.hanno.hpengine.engine.scene.Scene

class ProbeSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, emptyList()), StateConsumer {
    val strategy = ProbeRenderStrategy(engine)

    init {
        scene.renderStateConsumers.add(this)
    }

    override fun update(deltaSeconds: Float) {
    }

    override fun consume(state: RenderState) {
        strategy.renderProbes(state)
    }

}
