package de.hanno.hpengine.graphics.state

import de.hanno.struct.Struct

class DirectionalLightState : Struct() {
    val viewMatrix by de.hanno.hpengine.graphics.HpMatrix()
    val projectionMatrix by de.hanno.hpengine.graphics.HpMatrix()
    val viewProjectionMatrix by de.hanno.hpengine.graphics.HpMatrix()
    val color by de.hanno.hpengine.scene.HpVector3f()
    val dummy by 0F
    val direction by de.hanno.hpengine.scene.HpVector3f()
    var scatterFactor by 0F
    var shadowMapHandle by 0L
    var shadowMapId by 0
    val dummy0 by 0F
}