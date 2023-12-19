package de.hanno.hpengine.skybox

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Extractor
import org.koin.core.annotation.Single


class SkyBoxComponent : Component()

@All(SkyBoxComponent::class)
@Single(binds = [BaseSystem::class, SkyBoxSystem::class])
class SkyBoxSystem(
    private val materialSystem: MaterialSystem,
    private val skyBoxStateHolder: SkyBoxStateHolder,
    private val config: Config,
) : BaseEntitySystem(), WorldPopulator, Extractor {
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var tagManager: TagManager

    override fun processSystem() {
        if (!tagManager.isRegistered(primaryCameraTag)) return

        val primaryCameraEntityId = tagManager.getEntityId(primaryCameraTag)

        if (subscription.entities.size() > 0) {
            subscription.entities.data.firstOrNull()?.let { skyBoxEntityId ->
                val transform = transformComponentMapper[skyBoxEntityId].transform
                val primaryCameraTransform = transformComponentMapper[primaryCameraEntityId].transform

                val eyePosition = primaryCameraTransform.position
                transform.identity().translate(eyePosition)
                transform.scale(1000f)
            }
        }
    }

    override fun extract(currentWriteState: RenderState) {
        forEachEntity {
            currentWriteState.set(skyBoxStateHolder.skyBoxMaterialIndex, materialSystem.indexOf(materialComponentMapper[it].material))
        }
    }

    override fun World.populate() {
        addSkyBox(config)
    }
}

@Single
class SkyBoxStateHolder(
    private val renderStateContext: RenderStateContext
) {
    var skyBoxMaterialIndex = renderStateContext.renderState.registerState { -1 }
}

fun World.addSkyBox(config: Config) {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "SkyBox"
        }
        create(TransformComponent::class.java)
        create(SkyBoxComponent::class.java)
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
                this.put(Material.MAP.ENVIRONMENT, getSystem(OpenGLTextureManager::class.java).cubeMap)
            }
        }
    }
}
