package de.hanno.hpengine.graphics.renderer.forward

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import EntityStruktImpl.Companion.type
import IntStruktImpl.Companion.type
import MaterialStruktImpl.Companion.type
import Matrix4fStruktImpl.Companion.sizeInBytes
import Matrix4fStruktImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import org.joml.Vector3f
import org.koin.core.annotation.Single
import org.koin.ksp.generated.module
import org.lwjgl.BufferUtils

@Single(binds = [RenderSystem::class])
class SimpleForwardRenderer(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val renderTarget: RenderTarget2D,
    private val programManager: ProgramManager,
    private val config: Config,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
): RenderSystem {

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("COLOR_OUTPUT_0", true)),
        StaticFirstPassUniforms(graphicsApi)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true), Define("COLOR_OUTPUT_0", true)),
        AnimatedFirstPassUniforms(graphicsApi)
    )

    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem) {
            override fun RenderState.extractRenderBatches() = this[defaultBatchesSystem.renderBatchesStatic]
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramAnimated,entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem) {
            override fun RenderState.extractRenderBatches() = this[defaultBatchesSystem.renderBatchesAnimated]

            override fun RenderState.selectVertexIndexBuffer() = this[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderStateContext.renderState.currentWriteState

        currentWriteState[staticDirectPipeline].prepare(currentWriteState)
        currentWriteState[animatedDirectPipeline].prepare(currentWriteState)
    }

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        cullFace = true
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LEQUAL
        blend = false

        renderTarget.use(true)
        profiled("MainPipeline") {
            renderState[staticDirectPipeline].draw(renderState)
            renderState[animatedDirectPipeline].draw(renderState)
        }
    }
}

// TODO: Remove those duplicated code (also present in deferred renderer module)
sealed class FirstPassUniforms(graphicsApi: GraphicsApi): Uniforms() {
    var materials by SSBO("Material", 1, graphicsApi.PersistentShaderStorageBuffer(1).typed(MaterialStrukt.type))
    var entities by SSBO("Entity", 3, graphicsApi.PersistentShaderStorageBuffer(1).typed(EntityStrukt.type))
    var entityOffsets by SSBO("int", 4, graphicsApi.PersistentShaderStorageBuffer(1).typed(IntStrukt.type))
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

open class StaticFirstPassUniforms(graphicsApi: GraphicsApi): FirstPassUniforms(graphicsApi) {
    var vertices by SSBO("VertexPacked", 7, graphicsApi.PersistentShaderStorageBuffer(1).typed(VertexStruktPacked.type))
}
open class AnimatedFirstPassUniforms(graphicsApi: GraphicsApi): FirstPassUniforms(graphicsApi) {
    var joints by SSBO("mat4", 6, graphicsApi.PersistentShaderStorageBuffer(Matrix4fStrukt.sizeInBytes).typed(
        Matrix4fStrukt.type))
    var vertices by SSBO("VertexAnimatedPacked", 7, graphicsApi.PersistentShaderStorageBuffer(
        AnimatedVertexStruktPacked.sizeInBytes).typed(
        AnimatedVertexStruktPacked.type
    )
    )
}

fun createTransformBuffer() = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }

val simpleForwardRendererModule = SimpleForwardRenderingModule().module