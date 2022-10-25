package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.renderer.pipelines.GpuBuffer
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class UniformDelegate<T>(var _value: T) : ReadWriteProperty<Uniforms, T> {
    lateinit var name: String
        internal set
    override fun getValue(thisRef: Uniforms, property: KProperty<*>): T = _value
    override fun setValue(thisRef: Uniforms, property: KProperty<*>, value: T) {
        _value = value
    }

}

class IntType(initial: Int = 0): UniformDelegate<Int>(initial)
class FloatType(initial: Float = 0f): UniformDelegate<Float>(initial)
class BooleanType(initial: Boolean): UniformDelegate<Boolean>(initial)
class Mat4(initial: FloatBuffer = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }) : UniformDelegate<FloatBuffer>(initial)
class Vec3(initial: Vector3f) : UniformDelegate<Vector3f>(initial)
class SSBO(val dataType: String, val bindingIndex: Int, initial: GpuBuffer) : UniformDelegate<GpuBuffer>(initial)

open class Uniforms {
    val registeredUniforms = mutableListOf<UniformDelegate<*>>()

    operator fun <T> UniformDelegate<T>.provideDelegate(thisRef: Uniforms, prop: KProperty<*>): ReadWriteProperty<Uniforms, T> {
        return this.apply {
            this.name = prop.name
            thisRef.registeredUniforms.add(this)
        }
    }
    companion object {
        val Empty = Uniforms()
    }
}