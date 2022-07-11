package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.graphics.HpMatrix
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.struct.Struct

class DirectionalLightState : Struct() {
    val viewMatrix by HpMatrix()
    val projectionMatrix by HpMatrix()
    val viewProjectionMatrix by HpMatrix()
    val color by HpVector3f()
    val dummy by 0F
    val direction by HpVector3f()
    var scatterFactor by 0F
    var shadowMapHandle by 0L
    var shadowMapId by 0
    val dummy0 by 0F
}