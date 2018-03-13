package de.hanno.hpengine.engine.graphics.light.tubelight

import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import org.joml.Vector3f

class TubeLightComponentSystem: SimpleComponentSystem<TubeLight>(theComponentClass = TubeLight::class.java, factory = { TubeLight(it, Vector3f(1f, 1f, 1f), 50f, 20f) })