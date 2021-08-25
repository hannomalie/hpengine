package de.hanno.hpengine.engine.extension

import de.hanno.hpengine.engine.ScriptComponentSystem
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.CameraRenderSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.camera.MovableInputComponentComponentSystem
import de.hanno.hpengine.engine.component.CustomComponentSystem
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.GIVolumeSystem
import de.hanno.hpengine.engine.component.ModelComponent
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
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightControllerComponentSystem
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
import de.hanno.hpengine.engine.graphics.renderer.extensions.AOScatteringExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.BvHPointLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.DirectionalLightSecondPassExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ForwardRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbeComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbeManager
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbeRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.EntityBuffer
import de.hanno.hpengine.engine.model.ModelComponentEntitySystem
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialInfo
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.OceanWaterComponenSystem
import de.hanno.hpengine.engine.scene.OceanWaterEntitySystem
import de.hanno.hpengine.engine.scene.OceanWaterRenderSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.scene.dsl.CameraDescription
import de.hanno.hpengine.engine.scene.dsl.CustomComponentDescription
import de.hanno.hpengine.engine.scene.dsl.DirectionalLightControllerComponentDescription
import de.hanno.hpengine.engine.scene.dsl.DirectionalLightDescription
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.MovableInputComponentDescription
import de.hanno.hpengine.engine.scene.dsl.SceneDescription
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.ressources.enhanced
import org.koin.core.component.get
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42

val baseModule = module {
    manager { EntityManager() }
    entitySystem { ModelComponentEntitySystem(get(), get(), get(), get(), get()) }

    addBackendModule()
    addCameraModule()
    addSkyboxModule()
    addReflectionProbeModule()
    addPointLightModule()
    addGIModule()
    addOceanWaterModule()
    addDirectionalLightModule()

    renderSystem { ExtensibleDeferredRenderer(get(), get(), get(), get(), get(), getAll()) }
    renderExtension { ForwardRenderExtension(get(), get(), get(), get()) }
    renderExtension { AOScatteringExtension(get(), get(), get(), get(), get()) }
//    TODO: Fails because of shader code errors
//    renderExtension { EvaluateProbeRenderExtension(get(), get(), get(), get(), get()) }

    manager { MaterialManager(get(), get(), get()) }
    manager { PhysicsManager(get(), get(), get(), get()) }

    scope<Scene> {
        scoped { AreaLightSystem(get(), get(), get(), get()) } binds arrayOf(EntitySystem::class, RenderSystem::class)
        scoped { EntityBuffer() }
    }
    componentSystem { AreaLightComponentSystem() }
    componentSystem { ModelComponentSystem() }
    componentSystem { TubeLightComponentSystem() }
    componentSystem { CustomComponentSystem() }
    componentSystem { ScriptComponentSystem() }
    componentSystem { ClustersComponentSystem() }
    componentSystem { InputComponentSystem() }
    componentSystem { MovableInputComponentComponentSystem() }
}

fun Module.addGIModule() {
    extension { GiVolumeExtension() }
    renderExtension { VoxelConeTracingExtension(get(), get(), get(), get(), get(), get()) }
    componentSystem { GiVolumeComponentSystem() }
    entitySystem { GIVolumeSystem(get()) }
}

fun Module.addPointLightModule() {
    renderExtension { BvHPointLightSecondPassExtension(get(), get(), get(), get(), get()) }
    componentSystem { PointLightComponentSystem() }
    scope<Scene> {
        scoped { PointLightSystem(get(), get(), get()) } binds (arrayOf(EntitySystem::class, RenderSystem::class))
    }
}

fun Module.addDirectionalLightModule() {
    extension { DirectionalLightExtension() }
    renderSystem { DirectionalLightRenderSystem() }
    renderExtension { DirectionalLightDeferredRenderingExtension(get(), get(), get(), get(), get()) }
    componentSystem { DirectionalLightControllerComponentSystem() }
    entitySystem { DirectionalLightSystem() }
}

fun Module.addOceanWaterModule() {
    renderSystem { OceanWaterRenderSystem(get(), get(), get(), get()) }
    componentSystem { OceanWaterComponenSystem() }
    entitySystem { OceanWaterEntitySystem(get()) }
}

fun Module.addReflectionProbeModule() {
    renderExtension { ReflectionProbeRenderExtension(get(), get(), get(), get(), get(), get()) }
    componentSystem { ReflectionProbeComponentSystem() }
    manager { ReflectionProbeManager(get()) }
}

fun Module.addCameraModule() {
    extension { CameraExtension() }
    renderSystem { CameraRenderSystem(get(), get(), get(), get()) }
    componentSystem { CameraComponentSystem(get(), get(), get(), get()) }
}

fun Module.addSkyboxModule() {
    extension { SkyboxExtension(get(), get(), get(), get()) }
    renderSystem { SkyboxExtension.SkyboxRenderSystem() }
    renderExtension {
        SkyboxExtension.SkyboxRenderExtension(get(), get(), get(), get(), get(), get())
    }
}

fun Module.addBackendModule() {
    single { AddResourceContext() }
    single { MBassadorEventBus() } bind EventBus::class

    single { TextureManager(get(), get(), get(), get()) } bind Manager::class
    single { OpenGLContext.invoke(get()) } bind GpuContext::class
    single { OpenGlProgramManager(get(), get(), get()) } binds arrayOf(ProgramManager::class, Manager::class)
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
    single { SceneManager(get()) } bind Manager::class
}

class GiVolumeComponentSystem: SimpleComponentSystem<GIVolumeComponent>(GIVolumeComponent::class.java)
class GiVolumeExtension : Extension {
    // TODO: Migrate to SceneDescription.decorate
//    override fun Scene.decorate() {
//        entity("GlobalGiGrid") {
//            addComponent(GIVolumeComponent(this, get<TextureManager>().createGIVolumeGrids(), Vector3f(100f)))
//            customComponent { scene, _ ->
//                boundingVolume.setLocalAABB(scene.aabb.min, scene.aabb.max)
//            }
//        }
//        // TODO: Global grid makes sense maybe, but this one shouldn't live here forever, but I use it for testing purposes
//        entity("SecondGiGrid") {
//            transform.translation(Vector3f(0f, 0f, 50f))
//            addComponent(GIVolumeComponent(this, get<TextureManager>().createGIVolumeGrids(), Vector3f(30f)))
//        }
//    }
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

class DirectionalLightExtension : Extension {
    override fun SceneDescription.decorate() {
        entity("DirectionalLight") {
            add(DirectionalLightControllerComponentDescription())
            add(DirectionalLightDescription())
        }
    }
}

class CameraExtension : Extension {
    override fun SceneDescription.decorate() {
        entity(cameraEntityName) {
            add(MovableInputComponentDescription())
            add(CameraDescription())
        }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
        renderState.camera.init(scene.activeCamera)
    }

    var _activeCameraEntity: Entity? = null
    var Scene.activeCameraEntity: Entity
        get() = _activeCameraEntity ?: cameraEntity
        set(value) { _activeCameraEntity = value }

    companion object {
        val cameraEntityName = "MainCamera"
        val Scene.cameraEntity: Entity
            get() = getEntity(cameraEntityName)!!

        val Scene.activeCameraEntity: Entity
            get() = get<CameraExtension>().run { this@activeCameraEntity.activeCameraEntity }

        val Scene.activeCamera: Camera
            get() = get<CameraExtension>().run { this@activeCamera.activeCameraEntity }.getComponent(Camera::class.java)!!

        val Scene.camera
            get() = cameraEntity.getComponent(Camera::class.java)!!

    }
}

class SkyboxExtension(
    val config: Config,
    programManager: ProgramManager<OpenGl>,
    gpuContext: GpuContext<OpenGl>,
    val textureManager: TextureManager
) : Extension {
    private val firstpassProgramVertexSource =
        config.EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource()
    private val firstpassProgramFragmentSource =
        config.EngineAsset("shaders/first_pass_fragment.glsl").toCodeSource()

    private val simpleColorProgramStatic = programManager.getProgram(
        firstpassProgramVertexSource,
        firstpassProgramFragmentSource.enhanced {
            replace(
                "//END",
                """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
            )
        }, StaticFirstPassUniforms(gpuContext) as FirstPassUniforms
    )

    init {
        gpuContext.bindTexture(
            6,
            GlTextureTarget.TEXTURE_CUBE_MAP,
            textureManager.cubeMap.id
        )
    }

    class SkyboxRenderSystem : RenderSystem {
        override fun extract(scene: Scene, renderState: RenderState) {
            renderState.skyBoxMaterialIndex =
                scene.getEntity("Skybox")?.getComponent(ModelComponent::class.java)?.material?.materialIndex ?: -1
        }
    }

    override fun SceneDescription.decorate() {
        entity("Skybox") {
            contributesToGi = false
            add(
                StaticModelComponentDescription(
                    "assets/models/skybox.obj",
                    Directory.Engine,
                    material = SimpleMaterial(
                        name = "Skybox",
                        materialInfo = MaterialInfo(
                            materialType = SimpleMaterial.MaterialType.UNLIT,
                            cullBackFaces = false,
                            isShadowCasting = false,
                            program = simpleColorProgramStatic
                        )
                    ).apply {
                        materialInfo.put(SimpleMaterial.MAP.ENVIRONMENT, textureManager.cubeMap)
                    }
                )
            )
            add(
                CustomComponentDescription { scene, entity, _ ->
                    val eyePosition = scene.get<CameraExtension>().run { scene.activeCameraEntity.transform.position }
                    entity.transform.identity().translate(eyePosition)
                    entity.transform.scale(1000f)
                }
            )
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
                it.getComponent(ModelComponent::class.java)?.model?.material?.materialInfo?.maps?.get(SimpleMaterial.MAP.ENVIRONMENT)
                    ?.let {
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
}

val Scene.skyBox: Entity?
    get() = getEntity("Skybox")