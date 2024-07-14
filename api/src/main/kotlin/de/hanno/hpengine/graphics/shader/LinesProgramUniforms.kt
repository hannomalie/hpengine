package de.hanno.hpengine.graphics.shader

import Vector4fStruktImpl.Companion.sizeInBytes
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.math.Vector4fStrukt
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class LinesProgramUniforms(
    graphicsApi: GraphicsApi
) : Uniforms() {
    var vertices by SSBO("vec4", 7, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(ElementCount(100), SizeInBytes(Vector4fStrukt.sizeInBytes))))
    val modelMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val color by Vec3(Vector3f(1f, 0f, 0f))
}