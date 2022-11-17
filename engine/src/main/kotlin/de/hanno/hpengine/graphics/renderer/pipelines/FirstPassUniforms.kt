package de.hanno.hpengine.graphics.renderer.pipelines

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.shader.*
import org.joml.Vector3f
import org.lwjgl.BufferUtils.createFloatBuffer

context(GpuContext)
sealed class FirstPassUniforms: Uniforms() {
    var materials by SSBO("Material", 1, PersistentMappedBuffer(1).typed(MaterialStrukt.type))
    var entities by SSBO("Entity", 3, PersistentMappedBuffer(1).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, PersistentMappedBuffer(1).typed(IntStrukt.type))
    var useRainEffect by BooleanType(false)
    var rainEffect by FloatType(0f)
    var viewMatrix by Mat4(createTransformBuffer())
    var lastViewMatrix by Mat4(createTransformBuffer())
    var projectionMatrix by Mat4(createTransformBuffer())
    var viewProjectionMatrix by Mat4(createTransformBuffer())

    var eyePosition by Vec3(Vector3f())
    var near by FloatType()
    var far by FloatType()
    var time by IntType()
    var useParallax by BooleanType(false)
    var useSteepParallax by BooleanType(false)

    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
    var indirect by BooleanType(true)
}
fun createTransformBuffer() = createFloatBuffer(16).apply { Transform().get(this) }

context(GpuContext)
open class StaticFirstPassUniforms: FirstPassUniforms() {
    var vertices by SSBO("VertexPacked", 7, PersistentMappedBuffer(1).typed(VertexStruktPacked.type))
}
context(GpuContext)
open class AnimatedFirstPassUniforms: FirstPassUniforms() {
    var joints by SSBO("mat4", 6, PersistentMappedBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type))
    var vertices by SSBO("VertexAnimatedPacked", 7, PersistentMappedBuffer(AnimatedVertexStruktPacked.sizeInBytes).typed(
        AnimatedVertexStruktPacked.type))
}

context(GpuContext)
fun IProgram<*>.setTextureUniforms(maps: Map<Material.MAP, Texture>) {
    for (mapEnumEntry in Material.MAP.values()) {

        if (maps.contains(mapEnumEntry)) {
            val map = maps[mapEnumEntry]!!
            if (map.id > 0) {
                bindTexture(mapEnumEntry.textureSlot, map)
                setUniform(mapEnumEntry.uniformKey, true)
            }
        } else {
            setUniform(mapEnumEntry.uniformKey, false)
        }
    }
}
