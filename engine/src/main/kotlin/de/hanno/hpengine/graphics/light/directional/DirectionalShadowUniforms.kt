package de.hanno.hpengine.graphics.light.directional

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import DirectionalLightStateImpl.Companion.type
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
import de.hanno.hpengine.graphics.shader.BooleanType
import de.hanno.hpengine.graphics.shader.IntType
import de.hanno.hpengine.graphics.shader.SSBO
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked

sealed class DirectionalShadowUniforms(graphicsApi: GraphicsApi) : Uniforms() {
    var materials by SSBO("Material", 1, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(MaterialStrukt.type.sizeInBytes)).typed(MaterialStrukt.type))
    var directionalLightState by SSBO(
        "DirectionalLightState", 2, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(DirectionalLightState.type.sizeInBytes)).typed(DirectionalLightState.type)
    )
    var entities by SSBO("Entity", 3, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(EntityStrukt.type.sizeInBytes)).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(IntStrukt.type.sizeInBytes)).typed(IntStrukt.type))

    var indirect by BooleanType(true)
    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
}

class AnimatedDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var joints by SSBO(
        "mat4",
        6,
        graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(Matrix4fStrukt.sizeInBytes)).typed(Matrix4fStrukt.type)
    )
    var vertices by SSBO(
        "VertexAnimatedPacked", 7, graphicsApi.PersistentShaderStorageBuffer(
            SizeInBytes(AnimatedVertexStruktPacked.sizeInBytes)
        ).typed(AnimatedVertexStruktPacked.type)
    )
}

class StaticDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var vertices by SSBO("VertexPacked", 7, graphicsApi.PersistentShaderStorageBuffer(SizeInBytes(VertexStruktPacked.type.sizeInBytes)).typed(VertexStruktPacked.type))
}