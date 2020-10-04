package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.ScriptComponentSystem
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.programManager
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.CameraComponentSystem
import de.hanno.hpengine.engine.camera.InputComponentSystem
import de.hanno.hpengine.engine.camera.MovableInputComponent
import de.hanno.hpengine.engine.component.CustomComponent.Companion.customComponent
import de.hanno.hpengine.engine.component.CustomComponentSystem
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.GIVolumeSystem
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.entity.EntitySystem
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.light.area.AreaLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightComponentSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.light.tube.TubeLightComponentSystem
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DirectionalLightShadowMapExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.ClustersComponentSystem
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.model.ModelComponentManager
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.CubeMap
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.ressources.enhanced
import org.joml.Vector3f

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
    @JvmDefault fun Scene.onInit() { }

    fun init(sceneManager: SceneManager) {
        // TODO: Include other components here
        manager?.init(sceneManager)
    }
}


class BaseExtensions private constructor(val engineContext: EngineContext, private val extensions: MutableList<Extension>): List<Extension> by extensions {
    constructor(engineContext: EngineContext): this(engineContext, mutableListOf())

    private fun <T: Extension> T.add(): T = also { extensions.add(it) }

    val renderManagerExtension = RenderManager(engineContext)
    val physicsExtension = PhysicsExtension(engineContext)
    val directionalLightExtension = DirectionalLightExtension(engineContext).add()
    val cameraExtension = CameraExtension(engineContext).add()
    val pointLightExtension = PointLightExtension(engineContext).add()
    val areaLightExtension = AreaLightExtension(engineContext).add()
    val tubeLightExtension = TubeLightExtension(engineContext).add()
    val materialExtension = MaterialExtension(engineContext).add()
    val customComponentExtension = CustomComponentExtension(engineContext).add()
    val scriptComponentExtension = ScriptComponentExtension(engineContext).add()
    val clustersComponentExtension = ClustersComponentExtension(engineContext).add()
    val inputComponentExtension = InputComponentExtension(engineContext).add()
    val modelComponentExtension = ModelComponentExtension(engineContext, materialExtension.manager).add()
    val skyboxExtension = SkyboxExtension(engineContext).add()
}
class RenderManagerExtension(val engineContext: EngineContext): Extension {
    override val manager = RenderManager(engineContext)
}
class PhysicsExtension(val engineContext: EngineContext): Extension {
    override val manager = PhysicsManager(engineContext, config = engineContext.config)
    override val renderSystem = manager
}
class ModelComponentExtension(val engineContext: EngineContext, materialManager: MaterialManager): Extension {
    override val manager = ModelComponentManager()
    override val componentSystem = ModelComponentSystem(engineContext, manager, materialManager)
}
class InputComponentExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = InputComponentSystem(engineContext)
}

class ClustersComponentExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = ClustersComponentSystem()
}

class ScriptComponentExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = ScriptComponentSystem()
}
class CustomComponentExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = CustomComponentSystem()
}
class MaterialExtension(val engineContext: EngineContext): Extension {
    override val manager = MaterialManager(engineContext)
}
class GiVolumeExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = SimpleComponentSystem(GIVolumeComponent::class.java)
    override val componentClass: Class<*> = GIVolumeComponent::class.java
    override val entitySystem = GIVolumeSystem(engineContext)
    override val deferredRendererExtension = entitySystem.voxelConeTracingExtension
    override fun Scene.onInit() {
        entity("GlobalGiGrid") {
            addComponent(GIVolumeComponent(this, engineContext.textureManager.createGIVolumeGrids(), Vector3f(100f)))
        }
        // TODO: Global grid makes sense maybe, but this one shouldn't live here forever, but I use it for testing purposes
        entity("SecondGiGrid") {
            transform.translation(Vector3f(0f,0f,50f))
            addComponent(GIVolumeComponent(this, engineContext.textureManager.createGIVolumeGrids(), Vector3f(30f)))
        }
    }
}

class DirectionalLightExtension(val engineContext: EngineContext): Extension {
    override val deferredRendererExtension = DirectionalLightShadowMapExtension(engineContext)
    override val entitySystem = DirectionalLightSystem(engineContext)
    override val renderSystem = entitySystem
    override fun Scene.onInit() {
        entity("DirectionalLight") {
            addComponent(DirectionalLight(this))
            addComponent(DirectionalLight.DirectionalLightController(engineContext, this))
        }
    }
}
class TubeLightExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = TubeLightComponentSystem()
}

class CameraExtension(val engineContext: EngineContext): Extension {
    override val componentClass: Class<*> = Camera::class.java
    override val componentSystem = CameraComponentSystem(engineContext)
    override val renderSystem = componentSystem
    override fun Scene.onInit() {
        entity(cameraEntityName) {
            addComponent(MovableInputComponent(engineContext, this))
            addComponent(baseExtensions.cameraExtension.componentSystem.create(this))
        }
    }
    companion object {
        val cameraEntityName = "MainCamera"
        val Scene.cameraEntity: Entity
            get() = getEntity(cameraEntityName)!!

        val Scene.camera
            get() = cameraEntity.getComponent(Camera::class.java)!!

    }
}

class EnvironmentProbeExtension(val engineContext: EngineContext): Extension {
    private val environmentProbeManager = EnvironmentProbeManager(engineContext)
    override val manager = environmentProbeManager
    override val renderSystem = environmentProbeManager
}
class SkyboxExtension(val engineContext: EngineContext): Extension {
    class SkyBox(var cubeMap: CubeMap)

    private val firstpassProgramVertexSource = engineContext.config.EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource()
    private val firstpassProgramFragmentSource = engineContext.config.EngineAsset("shaders/first_pass_fragment.glsl").toCodeSource()

    private val simpleColorProgramStatic = engineContext.programManager.getProgram(
            firstpassProgramVertexSource,
            firstpassProgramFragmentSource.enhanced {
                replace(
                        "//END",
                        """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
                )
            }
    )
    init {
        engineContext.gpuContext.bindTexture(6, GlTextureTarget.TEXTURE_CUBE_MAP, engineContext.textureManager.cubeMap.id)
    }
    override fun Scene.onInit() {
        entity("Skybox") {
            modelComponent(
                    name = "Plane",
                    file = "assets/models/skybox.obj",
                    materialManager = this@onInit.materialManager,
                    modelComponentManager = this@onInit.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir,
                    program = simpleColorProgramStatic
            ).apply {
                material.materialInfo.apply {
                    materialType = SimpleMaterial.MaterialType.UNLIT
                    cullBackFaces = false
                    isShadowCasting = false
                }
            }
            customComponent { scene, _ ->
                val camPosition = scene.activeCamera.getPosition()
                this@entity.transform.identity().scaleAroundLocal(1000f, camPosition.x, camPosition.y, camPosition.z)
                this@entity.transform.translate(camPosition)
            }
        }
    }
}

class PointLightExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = PointLightComponentSystem()
    override val renderSystem = PointLightSystem(engineContext)
}

class AreaLightExtension(val engineContext: EngineContext): Extension {
    override val componentSystem = AreaLightComponentSystem()
    override val renderSystem = AreaLightSystem(engineContext)
}