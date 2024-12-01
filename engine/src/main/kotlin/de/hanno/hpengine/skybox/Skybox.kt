package de.hanno.hpengine.skybox

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.forFirstEntityIfPresent
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.state.Box
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.model.BoundingVolumeComponent
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single


class SkyBoxComponent : Component()

@All(SkyBoxComponent::class)
@Single(binds = [BaseSystem::class, SkyBoxSystem::class])
class SkyBoxSystem(
    private val materialSystem: MaterialSystem,
    private val skyBoxStateHolder: SkyBoxStateHolder,
    private val config: Config,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val textureManager: TextureManager,
) : BaseEntitySystem(), WorldPopulator, Extractor {
    private val logger = LogManager.getLogger(SkyBoxSystem::class.java)
    init {
        logger.info("Creating system")
    }

    private val cubeMap = StaticHandleImpl(textureManager.getCubeMap(
        "assets/textures/skybox/skybox.png",
        config.directories.engineDir.resolve("assets/textures/skybox/skybox.png")
    ), uploadState = UploadState.Uploaded, currentMipMapBias = 0f) // TODO: Verify if just setting this is okay

    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var tagManager: TagManager

    override fun processSystem() {
        if (!tagManager.isRegistered(primaryCameraTag)) return

        val primaryCameraEntityId = tagManager.getEntityId(primaryCameraTag)

        forFirstEntityIfPresent { skyBoxEntityId ->
            logger.trace("SkyBox present (id $skyBoxEntityId), processing")
            val transform = transformComponentMapper[skyBoxEntityId].transform
            val primaryCameraTransform = transformComponentMapper[primaryCameraEntityId].transform

            val eyePosition = primaryCameraTransform.position
            transform.identity().translate(eyePosition)
            transform.scale(1000f)
        }
    }

    override fun extract(currentWriteState: RenderState) {
        forEachEntity { entityId ->
            currentWriteState.set(skyBoxStateHolder.skyBoxMaterialIndex, materialSystem.indexOf(materialComponentMapper[entityId].material))
            currentWriteState[defaultBatchesSystem.renderBatchesStatic].firstOrNull { batch ->
                batch.entityId == entityId
            }?.let { batch ->
                currentWriteState[skyBoxStateHolder.batch].underlying = batch
            }
        }
    }

    override fun World.populate() {
        addSkyBox(config, cubeMap)
    }
}

@Single
class SkyBoxStateHolder(renderStateContext: RenderStateContext) {
    var skyBoxMaterialIndex = renderStateContext.renderState.registerState { -1 }
    val batch = renderStateContext.renderState.registerState { Box(RenderBatch()) }
}

fun World.addSkyBox(config: Config, cubeMap: StaticHandleImpl<CubeMap>) {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "SkyBox"
        }
        create(TransformComponent::class.java)
        create(SkyBoxComponent::class.java)
        create(BoundingVolumeComponent::class.java).apply {
            this.boundingVolume.localMin.set(-5f)
            this.boundingVolume.localMin.set(5f)
        }
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription(
                "assets/models/skybox.obj",
                Directory.Engine,
            )
        }
        create(MaterialComponent::class.java).apply {
            material = Material(
                name = "Skybox",
                materialType = Material.MaterialType.UNLIT,
                cullBackFaces = false,
                renderPriority = -1,
                writesDepth = false,
                depthTest = true,
                isShadowCasting = false,
                neverCull = true,
                programDescription = ProgramDescription(
                    vertexShaderSource = config.EngineAsset("shaders/first_pass_vertex.glsl").toCodeSource(),
                    fragmentShaderSource = config.EngineAsset("shaders/first_pass_fragment.glsl").toCodeSource().enhanced(
                            name = "skybox_first_pass_fragment",
                            replacements = arrayOf(
                                "//END" to """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
                            )
                        )
                )
            ).apply {
                this.put(Material.MAP.ENVIRONMENT, cubeMap)
            }
        }
    }
}
