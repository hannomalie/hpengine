package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.graphics.HpMatrix
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.struct.Struct
import de.hanno.struct.Structable

class DirectionalLightState(parent: Structable? = null) : Struct(parent) {
    val viewMatrix by HpMatrix(this)
    val projectionMatrix by HpMatrix(this)
    val viewProjectionMatrix by HpMatrix(this)
    val color by HpVector3f(this)
    val dummy by 0F
    val direction by HpVector3f(this)
    var scatterFactor by 0F
    var shadowMapHandle by 0L
    var shadowMapId by 0
    val dummy0 by 0F
}