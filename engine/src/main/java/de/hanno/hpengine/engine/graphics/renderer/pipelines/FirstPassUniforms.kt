package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.EntityStruct
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.BooleanType
import de.hanno.hpengine.engine.graphics.shader.FloatType
import de.hanno.hpengine.engine.graphics.shader.IntType
import de.hanno.hpengine.engine.graphics.shader.Mat4
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.SSBO
import de.hanno.hpengine.engine.graphics.shader.Uniforms
import de.hanno.hpengine.engine.graphics.shader.Vec3
import de.hanno.hpengine.engine.graphics.shader.useAndBind
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.math.Matrix4f
import de.hanno.hpengine.engine.model.material.MaterialStruct
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.AnimatedVertexStructPacked
import de.hanno.hpengine.engine.scene.VertexStructPacked
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector3f
import org.lwjgl.BufferUtils


sealed class FirstPassUniforms(gpuContext: GpuContext<*>): Uniforms() {
    var materials by SSBO("Material", 1, PersistentMappedStructBuffer(1, gpuContext, { MaterialStruct() }))
    var entities by SSBO("Entity", 3, PersistentMappedStructBuffer(1, gpuContext, { EntityStruct() }))
    var entityOffsets by SSBO("int", 4, PersistentMappedStructBuffer(1, gpuContext, { IntStruct() }))
    var useRainEffect by BooleanType(false)
    var rainEffect by FloatType(0f)
    var viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    var lastViewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    var projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    var viewProjectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })

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
open class StaticFirstPassUniforms(gpuContext: GpuContext<*>): FirstPassUniforms(gpuContext) {
    var vertices by SSBO("VertexPacked", 7, PersistentMappedStructBuffer(1, gpuContext, { VertexStructPacked() }))
}
open class AnimatedFirstPassUniforms(gpuContext: GpuContext<*>): FirstPassUniforms(gpuContext) {
    var joints by SSBO("mat4", 6, PersistentMappedStructBuffer(1, gpuContext, { Matrix4f() }))
    var vertices by SSBO("VertexAnimatedPacked", 7, PersistentMappedStructBuffer(1, gpuContext, { AnimatedVertexStructPacked() }))
}

fun Program<out FirstPassUniforms>.setUniforms(renderState: RenderState, camera: Camera = renderState.camera, config: Config) {

    val viewMatrixAsBuffer = camera.viewMatrixAsBuffer
    val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
    val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer

    useAndBind { uniforms ->
        uniforms.apply {
            materials = renderState.materialBuffer
            entities = renderState.entitiesBuffer
            when(this) {
                is StaticFirstPassUniforms -> vertices = renderState.vertexIndexBufferStatic.vertexStructArray
                is AnimatedFirstPassUniforms -> {
                    joints = renderState.entitiesState.jointsBuffer
                    vertices = renderState.vertexIndexBufferAnimated.animatedVertexStructArray
                }
            }
            useRainEffect = config.effects.rainEffect != 0.0f
            rainEffect = config.effects.rainEffect
            viewMatrix = viewMatrixAsBuffer
            lastViewMatrix = viewMatrixAsBuffer
            projectionMatrix = projectionMatrixAsBuffer
            viewProjectionMatrix = viewProjectionMatrixAsBuffer

            eyePosition = camera.getPosition()
            near = camera.near
            far = camera.far
            time = renderState.time.toInt()
            useParallax = config.quality.isUseParallax
            useSteepParallax = config.quality.isUseSteepParallax
        }
    }
}

fun Program<*>.setTextureUniforms(maps: Map<SimpleMaterial.MAP, Texture>) {
    for (mapEnumEntry in SimpleMaterial.MAP.values()) {

        if (maps.contains(mapEnumEntry)) {
            val map = maps[mapEnumEntry]!!
            if (map.id > 0) {
                gpuContext.bindTexture(mapEnumEntry.textureSlot, map)
                setUniform(mapEnumEntry.uniformKey, true)
            }
        } else {
            setUniform(mapEnumEntry.uniformKey, false)
        }
    }
}