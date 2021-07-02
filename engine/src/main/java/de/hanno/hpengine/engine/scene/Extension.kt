package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.ScriptComponentSystem
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.camera.*
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.CustomComponentSystem
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.GIVolumeSystem
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntityManager
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightRenderSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.CompoundExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DirectionalLightShadowMapExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.engine.graphics.renderer.extensions.*
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.ModelComponentManager
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.ressources.enhanced
import org.joml.Vector3f
import org.koin.core.component.get
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42


interface Extension {
    val manager: Manager?
        get() = null
    val componentSystem: ComponentSystem<*>?
        get() = null
    val componentClass: Class<*>?
        get() = null

    val entitySystem: EntitySystem?
        get() = null
    val renderSystem: RenderSystem?
        get() = null
    val deferredRendererExtension: RenderExtension<OpenGl>?
        get() = null

    fun extract(scene: Scene, renderState: RenderState) { }
    fun Scene.decorate() { }

    fun init(sceneManager: SceneManager) {
        // TODO: Include other components here
        manager?.init(sceneManager)
    }
}

//class SceneScope
typealias SceneScope = Scene

val baseModule = module {

    single { AddResourceContext() }
    single { MBassadorEventBus() } bind EventBus::class

    single { TextureManager(get(), get(), get(), get()) }
    single { OpenGLContext.invoke(get()) } bind GpuContext::class
    single { OpenGlProgramManager(get(), get(), get()) } bind ProgramManager::class
    single { Input(get(), get()) }
    single { OpenGlBackend(get(), get(), get(), get(), get(), get()) } bind Backend::class
    single {
        val config: Config = get()
        val gpuContext: GpuContext<OpenGl> = get()
        DeferredRenderingBuffer(
            gpuContext,
            config.width,
            config.height
        )
    }
    single { RenderManager(get(), get(), get(), get(), get(), get(), get(), getAll()) } bind Manager::class
    single {
        val gpuContext: GpuContext<OpenGl> = get()
        RenderStateManager { RenderState(gpuContext) }
    }
    single { ExtensibleDeferredRenderer(get(), get(), get(), get(), get(), getAll()) } bind RenderSystem::class

    single { EngineContext(get(), get(), get(), get(), get(), renderSystems = getAll(), get()) }
    single { Engine(get(), get(), get()) }

    single { SceneManager(get()) } bind Manager::class

    single { DirectionalLightExtension(get()) } bind Extension::class
    single { CameraExtension(get()) } bind Extension::class
    single { SkyboxExtension(get()) } bind Extension::class

    single { BvHPointLightSecondPassExtension(get(), get(), get(), get(), get()) } bind RenderExtension::class
    single { ForwardRenderExtension(get(), get(), get(), get()) } bind RenderExtension::class
    single { ReflectionProbeRenderExtension(get(), get(), get(), get(), get(), get()) } bind RenderExtension::class

    "GI stuff".apply {
//        single { GiVolumeExtension() } bind Extension::class
//        single { VoxelConeTracingExtension(get(), get(), get(), get(), get(), get()) } bind RenderExtension::class
//
//        scope<SceneScope> {
//            scoped { GiVolumeComponentSystem() } bind ComponentSystem::class
//            scoped { GIVolumeSystem(get(), get()) } bind EntitySystem::class
//        }
    }

    single { CameraRenderSystem(get(), get(), get(), get()) } bind RenderSystem::class
    single { AOScatteringExtension(get(), get(), get(), get(), get()) } bind RenderExtension::class

    single { OceanWaterRenderSystem(get(), get(), get(), get()) } bind RenderSystem::class

    single { DirectionalLightRenderSystem() } bind RenderSystem::class
    single { SkyboxExtension.SkyboxRenderSystem() } bind RenderSystem::class
    single { SkyboxExtension.SkyboxRenderExtension(get(), get(), get(), get(), get(), get()) } bind RenderExtension::class
    single { DirectionalLightDeferredRenderingExtension(get(), get(), get(), get(), get()) } bind RenderExtension::class



    factory { Scene() }

    scope<SceneScope> {
        scoped { EntityManager() } bind Manager::class
        scoped { MaterialManager(config = get(), textureManager = get(), singleThreadContext = get()) } bind Manager::class
        scoped { ModelComponentManager() } bind Manager::class
        scoped { ModelComponentSystem(get(), get(), get()) } bind ComponentSystem::class
        scoped { PhysicsManager(get(), get(), get(), get()) } bind Manager::class
        scoped { DirectionalLightSystem() } bind EntitySystem::class
        scoped { CameraComponentSystem(get(), get(), get(), get()) } bind ComponentSystem::class
        scoped { PointLightComponentSystem() } bind ComponentSystem::class
        scoped { PointLightSystem(get()) } binds(arrayOf(EntitySystem::class, RenderSystem::class))
        scoped { AreaLightComponentSystem() } bind ComponentSystem::class
        scoped { AreaLightSystem(get()) } bind EntitySystem::class
        scoped { TubeLightComponentSystem() } bind ComponentSystem::class
        scoped { CustomComponentSystem() } bind ComponentSystem::class
        scoped { ScriptComponentSystem() } bind ComponentSystem::class
        scoped { ClustersComponentSystem() } bind ComponentSystem::class
        scoped { InputComponentSystem(get()) } bind ComponentSystem::class
        scoped { ReflectionProbeComponentSystem() } bind ComponentSystem::class
        scoped { ReflectionProbeManager(get()) } bind Manager::class
        scoped { OceanWaterComponenSystem() } bind ComponentSystem::class
        scoped { OceanWaterEntitySystem(get()) } bind EntitySystem::class
    }
}

class GiVolumeComponentSystem: SimpleComponentSystem<GIVolumeComponent>(GIVolumeComponent::class.java)
class GiVolumeExtension : Extension {
    override fun Scene.decorate() {
        entity("GlobalGiGrid") {
            addComponent(GIVolumeComponent(this, get<TextureManager>().createGIVolumeGrids(), Vector3f(100f)))
            customComponent { scene, _ ->
                boundingVolume.setLocalAABB(scene.aabb.min, scene.aabb.max)
            }
        }
        // TODO: Global grid makes sense maybe, but this one shouldn't live here forever, but I use it for testing purposes
        entity("SecondGiGrid") {
            transform.translation(Vector3f(0f, 0f, 50f))
            addComponent(GIVolumeComponent(this, get<TextureManager>().createGIVolumeGrids(), Vector3f(30f)))
        }
    }
}

class DirectionalLightDeferredRenderingExtension(
    config: Config,
    programManager: ProgramManager<OpenGl>,
    textureManager: TextureManager,
    gpuContext: GpuContext<OpenGl>,
    deferredRenderingBuffer: DeferredRenderingBuffer
): CompoundExtension<OpenGl>(
    listOf(
        DirectionalLightShadowMapExtension(config, programManager, textureManager, gpuContext, deferredRenderingBuffer),
        DirectionalLightSecondPassExtension(config, programManager, textureManager, gpuContext, deferredRenderingBuffer)
    )
)
class DirectionalLightExtension(val engineContext: EngineContext) : Extension {
    override fun Scene.decorate() {
        entity("DirectionalLight") {
            addComponent(DirectionalLight(this))
            addComponent(DirectionalLight.DirectionalLightController(engineContext, this))
        }
    }
}

class CameraExtension(val engineContext: EngineContext) : Extension {
    override fun Scene.decorate() = add(_cameraEntity)

    override fun extract(scene: Scene, renderState: RenderState) {
        renderState.camera.init(activeCamera)
    }

    private val _cameraEntity = newEntity(cameraEntityName) {
        addComponent(MovableInputComponent(engineContext, this))
        addComponent(
            Camera(engineContext, this)
        )
    }
    var activeCameraEntity: Entity = _cameraEntity.apply {
        activeCameraEntity = this
    }

    val activeCamera: Camera
        get() = activeCameraEntity.getComponent(Camera::class.java)!!

    val Entity.isActiveCameraEntity get() = this == activeCameraEntity

    companion object {
        val cameraEntityName = "MainCamera"
        val Scene.cameraEntity: Entity
            get() = getEntity(cameraEntityName)!!

        val Scene.camera
            get() = cameraEntity.getComponent(Camera::class.java)!!

    }
}

class SkyboxExtension(val engineContext: EngineContext) : Extension {
    private val firstpassProgramVertexSource =
        engineContext.config.EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource()
    private val firstpassProgramFragmentSource =
        engineContext.config.EngineAsset("shaders/first_pass_fragment.glsl").toCodeSource()

    private val simpleColorProgramStatic = engineContext.programManager.getProgram(
        firstpassProgramVertexSource,
        firstpassProgramFragmentSource.enhanced {
            replace(
                "//END",
                """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
            )
        }, StaticFirstPassUniforms(engineContext.gpuContext) as FirstPassUniforms
    )

    init {
        engineContext.gpuContext.bindTexture(
            6,
            GlTextureTarget.TEXTURE_CUBE_MAP,
            engineContext.textureManager.cubeMap.id
        )
    }

    private fun SkyBoxEntity(): Entity = Entity("Skybox").apply {
        modelComponent(
            name = "Plane",
            file = "assets/models/skybox.obj",
            textureManager = engineContext.textureManager,
            directory = engineContext.config.directories.engineDir
        ).apply {
            val materialInfo = material.materialInfo.copy(
                materialType = SimpleMaterial.MaterialType.UNLIT,
                cullBackFaces = false,
                isShadowCasting = false,
                program = simpleColorProgramStatic
            ).apply {
                put(SimpleMaterial.MAP.ENVIRONMENT, engineContext.textureManager.cubeMap)
            }
            material = SimpleMaterial(
                "skybox",
                materialInfo
            ) // TODO Investigate why this doesnt update in UI
        }
        customComponent { scene, _ ->
            val eyePosition = scene.get<CameraExtension>().activeCamera.getPosition()
            this.transform.identity().translate(eyePosition)
            this.transform.scale(1000f)
        }
    }

    class SkyboxRenderSystem : RenderSystem {
        override fun extract(scene: Scene, renderState: RenderState) {
            renderState.skyBoxMaterialIndex = scene.getEntity("Skybox")?.getComponent(ModelComponent::class.java)?.material?.materialIndex ?: -1
        }
    }

    class SkyboxRenderExtension(
        val config: Config,
        val gpuContext: GpuContext<OpenGl>,
        val deferredRenderingBuffer: DeferredRenderingBuffer,
        val programManager: ProgramManager<OpenGl>,
        val textureManager: TextureManager,
        val renderStateManager: RenderStateManager
    ) : RenderExtension<OpenGl> {

        private val secondPassReflectionProgram = programManager.getComputeProgram(
            config.EngineAsset("shaders/second_pass_skybox_reflection.glsl")
        )
        val skyBoxTexture = renderStateManager.renderState.registerState {
            IntStruct().apply {
                value = textureManager.cubeMap.id
            }
        }

        override fun extract(scene: Scene, renderState: RenderState) {
            scene.skyBox?.let {
                it.getComponent(ModelComponent::class.java)?.model?.material?.materialInfo?.maps?.get(SimpleMaterial.MAP.ENVIRONMENT)?.let {
                    renderState[skyBoxTexture].value = it.id
                }
            }
        }

        override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {

            gpuContext.bindTexture(0, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.positionMap)
            gpuContext.bindTexture(1, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.normalMap)
            gpuContext.bindTexture(2, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.colorReflectivenessMap)
            gpuContext.bindTexture(3, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.motionMap)
            gpuContext.bindTexture(4, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.lightAccumulationMapOneId)
            gpuContext.bindTexture(5, GlTextureTarget.TEXTURE_2D, deferredRenderingBuffer.visibilityMap)
            gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_CUBE_MAP, renderState[skyBoxTexture].value)
            // TODO: Add glbindimagetexture to openglcontext class
            GL42.glBindImageTexture(
                4,
                deferredRenderingBuffer.reflectionBuffer.renderedTextures[0],
                0,
                false,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_RGBA16F
            )
            GL42.glBindImageTexture(
                7,
                deferredRenderingBuffer.reflectionBuffer.renderedTextures[1],
                0,
                false,
                0,
                GL15.GL_READ_WRITE,
                GL30.GL_RGBA16F
            )
            secondPassReflectionProgram.use()
            secondPassReflectionProgram.setUniform("screenWidth", config.width.toFloat())
            secondPassReflectionProgram.setUniform("screenHeight", config.height.toFloat())
            secondPassReflectionProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.viewMatrixAsBuffer)
            secondPassReflectionProgram.setUniformAsMatrix4(
                "projectionMatrix",
                renderState.camera.projectionMatrixAsBuffer
            )
            secondPassReflectionProgram.bindShaderStorageBuffer(1, renderState.materialBuffer)
            secondPassReflectionProgram.dispatchCompute(
                config.width / 16,
                config.height / 16,
                1
            )
        }
    }

    override fun Scene.decorate() {
        add(SkyBoxEntity())
    }
}

val Scene.skyBox: Entity?
    get() = getEntity("Skybox")
