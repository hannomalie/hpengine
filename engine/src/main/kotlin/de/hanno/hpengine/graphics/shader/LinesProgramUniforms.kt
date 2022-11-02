package de.hanno.hpengine.graphics.shader

import Vector4fStruktImpl.Companion.sizeInBytes
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.math.Vector4fStrukt
import org.joml.Vector3f
import org.lwjgl.BufferUtils

class LinesProgramUniforms(gpuContext: GpuContext<*>) : Uniforms() {
    var vertices by SSBO("vec4", 7, PersistentMappedBuffer(100 * Vector4fStrukt.sizeInBytes, gpuContext))
    val modelMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val color by Vec3(Vector3f(1f, 0f, 0f))
}