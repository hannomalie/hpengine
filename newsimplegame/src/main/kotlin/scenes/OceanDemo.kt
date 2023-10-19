package scenes

import com.artemis.World
import de.hanno.hpengine.Engine
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.deferredRendererModule
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.ocean.OceanWaterComponent
import de.hanno.hpengine.ocean.OceanWaterRenderSystem
import de.hanno.hpengine.ocean.oceanModule
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.spatial.SpatialComponent
import de.hanno.hpengine.world.loadScene
import glfwModule
import invoke
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.koin.dsl.module
import java.io.File

fun main() {

    val config = Config(
        directories = Directories(
            EngineDirectory(File("""D:\workspace\hpengine\engine\src\main\resources\hp""")),
            GameDirectory(File("""D:\workspace\hpengine\newsimplegame\src\main\resources\game"""), null)
        ),
    )

    val engine = Engine(
        listOf(
            glfwModule,
            openglModule,
            deferredRendererModule,
            editorModule,
            oceanModule,
            module {
                single { config }
                single { config.gameDir }
                single { config.engineDir }
            }
        )
    )
    engine.world.loadScene {
        addOceanSurface(
            engine.systems.firstIsInstance<ProgramManager>(),
            engine.systems.firstIsInstance<OceanWaterRenderSystem>(),
            Vector3f()
        )
    }
    engine.simulate()
}

private var oceanSurfaceCounter = 0
private fun World.addOceanSurface(
    programManager: ProgramManager,
    oceanWaterRenderSystem: OceanWaterRenderSystem,
    translation: Vector3fc
) {
    edit(create()).apply {
        create(TransformComponent::class.java).apply {
            transform.scaling(10f)
            transform.translate(translation)
        }
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription(
                file = "assets/models/plane_tesselated.obj",
                Directory.Engine,
//                AABBData(
//                    Vector3f(-60f, -10f, -35f),
//                    Vector3f(60f, 130f, 50f)
//                ),
            )
        }
        create(MaterialComponent::class.java).apply {
            material = Material(
                "ocean",
                programDescription = programManager.heightMappingFirstPassProgramDescription.copy(
                    fragmentShaderSource = FileBasedCodeSource(programManager.config.engineDir.resolve("shaders/ocean/heightmapping_ocean_fragment.glsl"))
                ),
                diffuse = Vector3f(0.05f, 0.05f, 0.4f),
                metallic = 0.95f,
                roughness = 0.001f,
                parallaxScale = 1f,
                parallaxBias = 0.0f,
                useWorldSpaceXZAsTexCoords = true,
                uvScale = Vector2f(0.05f)
            ).apply {
                maps.putIfAbsent(Material.MAP.DIFFUSE, oceanWaterRenderSystem.albedoMap)
                maps.putIfAbsent(Material.MAP.DISPLACEMENT, oceanWaterRenderSystem.displacementMap)
                maps.putIfAbsent(Material.MAP.NORMAL, oceanWaterRenderSystem.waterNormalMap)
            }
        }
        add(OceanWaterComponent())
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "OceanSurface${oceanSurfaceCounter++}"
        }
    }
}

//                This has cracks between the instances
//                addComponent(ClustersComponent(this@entity).apply {
//                    repeat(6) { x ->
//                        repeat(6) { z ->
//                            val instanceTransform = Transform().apply {
//                                scaling(10f)
//                                translateLocal(Vector3f(20f * (x-3), 0f, 20f * (z-3)))
//                            }
//                            addInstances(listOf(Instance(this@entity, spatial = TransformSpatial(instanceTransform, AABB()), transform = instanceTransform)))
//                        }
//                    }
//                })