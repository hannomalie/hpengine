package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.scene.Scene

class BatchingSystem(engine: Engine, scene: Scene): SimpleEntitySystem(engine, scene, listOf(ModelComponent::class.java, ClustersComponent::class.java)) {

    override fun update(deltaSeconds: Float) {
    }
}