package de.hanno.hpengine.graphics.light.directional

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.cycle.CycleSystem
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.graphics.profiled
import de.hanno.hpengine.graphics.rendertarget.OpenGLFrameBuffer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.EntitiesState
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.Texture2D
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.EntityMovementSystem
import org.apache.logging.log4j.LogManager
import org.joml.Vector4f
import org.koin.core.annotation.Single
import struktgen.api.forIndex


@Single(binds = [DirectionalLightShadowMapExtension::class, Extractor::class, RenderSystem::class, BaseSystem::class])
@All(ModelCacheComponent::class)
class DirectionalLightShadowMapExtension(
    private val graphicsApi: GraphicsApi,
    renderStateContext: RenderStateContext,
    private val config: Config,
    private val programManager: ProgramManager,
    private val directionalLightStateHolder: DirectionalLightStateHolder,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
    private val entityMovementSystem: EntityMovementSystem,
    private val cycleSystem: CycleSystem,
) : Extractor, BaseEntitySystem(), RenderSystem {
    private val logger = LogManager.getLogger(DirectionalLightShadowMapExtension::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var modelCacheComponentMapper: ComponentMapper<ModelCacheComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

    private val cachingHelper = CachingHelper(directionalLightStateHolder, renderStateContext)

    val renderTarget = graphicsApi.RenderTarget(
        OpenGLFrameBuffer(graphicsApi, graphicsApi.DepthBuffer(dynamicShadowMapResolution, dynamicShadowMapResolution)),
        dynamicShadowMapResolution,
        dynamicShadowMapResolution,
        emptyList<Texture2D>(),
        "DirectionalLight Shadow",
        Vector4f(1f, 1f, 1f, 1f),
    )

    val staticRenderTarget = graphicsApi.RenderTarget(
        OpenGLFrameBuffer(graphicsApi, graphicsApi.DepthBuffer(staticShadowMapResolution, staticShadowMapResolution)),
        staticShadowMapResolution,
        staticShadowMapResolution,
        emptyList<Texture2D>(),
        "DirectionalLight Static Shadow",
        Vector4f(1f, 1f, 1f, 1f),
    )

    private val staticPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(StaticDirectionalShadowUniforms(graphicsApi))
    }
    private val animatedPipeline = renderStateContext.renderState.registerState {
        DirectionalShadowMapPipeline(AnimatedDirectionalShadowUniforms(graphicsApi))
    }

    private fun DirectionalShadowMapPipeline(uniforms: DirectionalShadowUniforms) = DirectionalShadowMapPipeline(
        graphicsApi,
        entitiesStateHolder,
        materialSystem,
        directionalLightStateHolder,
        entityBuffer,
        defaultBatchesSystem,
        programManager.getProgram(
            FileBasedCodeSource(config.engineDir.resolve("shaders/directional_shadowmap_vertex.glsl")),
            FileBasedCodeSource(config.engineDir.resolve("shaders/shadowmap_fragment.glsl")),
            uniforms,
            when(uniforms) {
                is AnimatedDirectionalShadowUniforms -> Defines(Define("ANIMATED", true))
                is StaticDirectionalShadowUniforms -> Defines()
            }
        )
    )

    override fun processSystem() {}
    override fun inserted(entityId: Int) {
        cachingHelper.anyModelEntityAddedInCycle = cycleSystem.cycle
        super.inserted(entityId)
    }
    override fun extract(currentWriteState: RenderState) {
        if(world != null) {
            forEachEntity { entityId ->
                val hasMoved = entityMovementSystem.entityHasMoved(entityId)
                val model = modelCacheComponentMapper[entityId].model
                val isShadowCaster = materialComponentMapper.getOrNull(entityId)?.material?.isShadowCasting ?: model.materials.any { it.isShadowCasting }
                val isStatic = model.isStatic
                val isStaticAndHasMoved = hasMoved && isShadowCaster && isStatic
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
        const val dynamicShadowMapResolution = 2048
        const val staticShadowMapResolution = 8 * 1024
    }
}

