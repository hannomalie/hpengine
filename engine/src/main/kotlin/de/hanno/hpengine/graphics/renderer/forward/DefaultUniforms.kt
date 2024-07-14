package de.hanno.hpengine.graphics.renderer.forward

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import org.joml.Vector3f

sealed class DefaultUniforms(graphicsApi: GraphicsApi): Uniforms() {
    var materials by SSBO("Material", 1, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(MaterialStrukt.type.sizeInBytes)).typed(MaterialStrukt.type))
    var entities by SSBO("Entity", 3, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(EntityStrukt.type.sizeInBytes)).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(IntStrukt.type.sizeInBytes)).typed(IntStrukt.type))
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

open class AnimatedDefaultUniforms(graphicsApi: GraphicsApi): DefaultUniforms(graphicsApi) {
    var joints by SSBO("mat4", 6, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(Matrix4fStrukt.sizeInBytes)).typed(
        Matrix4fStrukt.type))
    var vertices by SSBO("VertexAnimatedPacked", 7, graphicsApi.PersistentShaderStorageBuffer(
        SizeInBytes(AnimatedVertexStruktPacked.sizeInBytes)).typed(
            AnimatedVertexStruktPacked.type
        )
    )
}

open class StaticDefaultUniforms(graphicsApi: GraphicsApi): DefaultUniforms(graphicsApi) {
    var vertices by SSBO("VertexPacked", 7, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(VertexStruktPacked.type.sizeInBytes)).typed(VertexStruktPacked.type))
}