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
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.AnimatedVertexStruktPacked
import de.hanno.hpengine.scene.VertexStruktPacked
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.EntityMovementSystem
import org.joml.Vector4f
import org.koin.core.annotation.Single
import struktgen.api.forIndex


@Single(binds = [DirectionalLightShadowMapExtension::class, Extractor::class, RenderSystem::class, BaseSystem::class])
@All(ModelCacheComponent::class)
class DirectionalLightShadowMapExtension(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    config: Config,
    programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val entityMovementSystem: EntityMovementSystem,
    private val cycleSystem: CycleSystem,
) : Extractor, BaseEntitySystem(), RenderSystem {
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>

    private val cachingHelper = CachingHelper(directionalLightStateHolder, renderStateContext)

    val renderTarget = graphicsApi.RenderTarget(
        OpenGLFrameBuffer(graphicsApi, graphicsApi.DepthBuffer(SHADOWMAP_RESOLUTION, SHADOWMAP_RESOLUTION)),
        SHADOWMAP_RESOLUTION,
        SHADOWMAP_RESOLUTION,
        emptyList<Texture2D>(),
        "DirectionalLight Shadow",
        Vector4f(1f, 1f, 1f, 1f),
    )

    val staticRenderTarget = graphicsApi.RenderTarget(
        OpenGLFrameBuffer(graphicsApi, graphicsApi.DepthBuffer(STATIC_SHADOWMAP_RESOLUTION, STATIC_SHADOWMAP_RESOLUTION)),
        STATIC_SHADOWMAP_RESOLUTION,
        STATIC_SHADOWMAP_RESOLUTION,
        emptyList<Texture2D>(),
        "DirectionalLight Static Shadow",
        Vector4f(1f, 1f, 1f, 1f),
    )

    private val staticDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        StaticDirectionalShadowUniforms(graphicsApi),
        Defines()
    )
    private val animatedDirectionalShadowPassProgram = programManager.getProgram(
        FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
        FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
        AnimatedDirectionalShadowUniforms(graphicsApi),
        Defines(Define("ANIMATED", true)),
    )

    private val staticPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(staticDirectionalShadowPassProgram)
    }
    private val animatedPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(animatedDirectionalShadowPassProgram)
    }

    inner class DirectionalShadowMapPipeline(val directionalShadowPassProgram: Program<out DirectionalShadowUniforms>) {
        private var verticesCount = 0
        private var entitiesCount = 0

        fun draw(renderState: RenderState, update: Update) = graphicsApi.run {
            profiled("Actual draw entities") {
                verticesCount = 0
                entitiesCount = 0
                val entitiesState = renderState[entitiesStateHolder.entitiesState]
                val program = directionalShadowPassProgram
                val vertexIndexBuffer = entitiesState.selectVertexIndexBuffer(program.uniforms)

                vertexIndexBuffer.indexBuffer.bind()

                program.use()
                program.uniforms.apply {
                    materials = renderState[materialSystem.materialBuffer]
                    directionalLightState = renderState[directionalLightStateHolder.lightState]
                    entities = renderState[entityBuffer.entitiesBuffer]
                    when (this) {
                        is StaticDirectionalShadowUniforms -> vertices = vertexIndexBuffer.vertexStructArray
                        is AnimatedDirectionalShadowUniforms -> {
                            vertices = vertexIndexBuffer.vertexStructArray
                            joints = entitiesState.jointsBuffer
                        }
                    }
                    entityBaseIndex = 0
                    indirect = false
                }

                val shadowCasters = defaultBatchesSystem.getRenderBatches(renderState, program.uniforms)
                for (batch in shadowCasters.filter { it.update == update }) {
                    program.uniforms.entityIndex = batch.entityBufferIndex
                    program.bind()
                    vertexIndexBuffer.indexBuffer.draw(batch.drawElementsIndirectCommand, false, PrimitiveType.Triangles, RenderingMode.Fill)
                    verticesCount += batch.vertexCount
                    entitiesCount += 1
                }
            }
        }

        private fun DefaultBatchesSystem.getRenderBatches(renderState: RenderState, uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> renderState[renderBatchesAnimated]
            is StaticDirectionalShadowUniforms -> renderState[renderBatchesStatic]
        }.filter { it.isVisible && it.isShadowCasting }

        private fun EntitiesState.selectVertexIndexBuffer(uniforms: DirectionalShadowUniforms) = when (uniforms) {
            is AnimatedDirectionalShadowUniforms -> vertexIndexBufferAnimated
            is StaticDirectionalShadowUniforms -> vertexIndexBufferStatic
        }
    }

    override fun processSystem() {}
    override fun inserted(entityId: Int) {
        cachingHelper.anyModelEntityAddedInCycle = cycleSystem.cycle
        super.inserted(entityId)
    }
    override fun extract(currentWriteState: RenderState) {
        if(world != null) {
            forEachEntity { entityId ->
                val isStaticAndHasMoved = entityMovementSystem.entityHasMoved(entityId) && modelCacheComponentMapper[entityId].model.isStatic
                if(isStaticAndHasMoved) {
                    cachingHelper.setStaticEntityHasMovedInCycle(currentWriteState, entityMovementSystem.cycleEntityHasMovedIn(entityId))
                }
            }
        }
        cachingHelper.extract(currentWriteState)

        currentWriteState[directionalLightStateHolder.lightState].typedBuffer.forIndex(0) {
            it.shadowMapHandle = renderTarget.frameBuffer.depthBuffer!!.texture.handle
            it.shadowMapId = renderTarget.frameBuffer.depthBuffer!!.texture.id
            it.staticShadowMapHandle = staticRenderTarget.frameBuffer.depthBuffer!!.texture.handle
            it.staticShadowMapId = staticRenderTarget.frameBuffer.depthBuffer!!.texture.id
        }
    }

    override fun render(renderState: RenderState) = graphicsApi.run {
        profiled("Directional shadowmap") {
            drawShadowMap(renderState, renderState[entitiesStateHolder.entitiesState])
        }
    }

    private fun drawShadowMap(renderState: RenderState, entitiesState: EntitiesState) = graphicsApi.run {
        blend = false
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LESS
        cullFace = false

        if (cachingHelper.animatedEntitiesNeedRerender(renderState, entitiesState)) {
            renderTarget.use(true)
            renderState[animatedPipeline].draw(renderState, update = Update.DYNAMIC)
            cachingHelper.animatedRenderedInCycle = renderState.cycle
        }

        if (cachingHelper.staticEntitiesNeedRerender(renderState)) {
            staticRenderTarget.use(true)
            renderState[staticPipeline].draw(renderState, update = Update.STATIC)
            cachingHelper.staticRenderedInCycle = renderState.cycle
        }

        cachingHelper.forceRerender = false
    }

    companion object {
        const val SHADOWMAP_RESOLUTION = 2048
        const val STATIC_SHADOWMAP_RESOLUTION = 8 * 1024
    }
}

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