package de.hanno.hpengine.graphics.light.directional

import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.math.Vector3fStrukt
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface DirectionalLightState : Strukt {
    context(ByteBuffer) val viewMatrix: Matrix4fStrukt
    context(ByteBuffer) val projectionMatrix: Matrix4fStrukt
    context(ByteBuffer) val viewProjectionMatrix: Matrix4fStrukt
    context(ByteBuffer) val color: Vector3fStrukt
    context(ByteBuffer) val dummy: Float

    context(ByteBuffer) val direction: Vector3fStrukt
    context(ByteBuffer) var scatterFactor: Float

    context(ByteBuffer) var shadowMapHandle: Long
    context(ByteBuffer) var shadowMapId: Int
    context(ByteBuffer) var staticShadowMapId: Int

    context(ByteBuffer) var staticShadowMapHandle: Long
    context(ByteBuffer) var dummy0: Int
    context(ByteBuffer) var dummy1: Int
    companion object
}