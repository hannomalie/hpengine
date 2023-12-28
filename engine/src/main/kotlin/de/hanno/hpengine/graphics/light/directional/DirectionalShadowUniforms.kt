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
    var materials by SSBO("Material", 1, graphicsApi.PersistentShaderStorageBuffer(1).typed(MaterialStrukt.type))
    var directionalLightState by SSBO(
        "DirectionalLightState", 2, graphicsApi.PersistentShaderStorageBuffer(1).typed(DirectionalLightState.type)
    )
    var entities by SSBO("Entity", 3, graphicsApi.PersistentShaderStorageBuffer(1).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, graphicsApi.PersistentShaderStorageBuffer(1).typed(IntStrukt.type))

    var indirect by BooleanType(true)
    var entityIndex by IntType(0)
    var entityBaseIndex by IntType(0)
}

class AnimatedDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var joints by SSBO(
        "mat4",
        6,
        graphicsApi.PersistentShaderStorageBuffer(Matrix4fStrukt.sizeInBytes).typed(Matrix4fStrukt.type)
    )
    var vertices by SSBO(
        "VertexAnimatedPacked", 7, graphicsApi.PersistentShaderStorageBuffer(
            AnimatedVertexStruktPacked.sizeInBytes
        ).typed(AnimatedVertexStruktPacked.type)
    )
}

class StaticDirectionalShadowUniforms(graphicsApi: GraphicsApi) : DirectionalShadowUniforms(graphicsApi) {
    var vertices by SSBO("VertexPacked", 7, graphicsApi.PersistentShaderStorageBuffer(1).typed(VertexStruktPacked.type))
}