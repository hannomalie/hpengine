package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.directory.GameAsset
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.programManager
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import kotlin.random.Random

val Engine.lotsOfPlanesScene
    get() = scene("LotsOfCubes") {
        entities {
            entity("Plane") {
                val simpleColorProgramStatic = programManager.getProgram(
                    GameAsset("shaders/first_pass_vertex.glsl"),
                    GameAsset("shaders/first_pass_fragment.glsl")
                )
                modelComponent(
                    name = "Plane",
                    file = "assets/models/planeRotated.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir,
                    program = simpleColorProgramStatic
                ).apply {
                    material.materialInfo.put(SimpleMaterial.MAP.DIFFUSE, engineContext.textureManager.getTexture("assets/textures/grass.png", true))
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                }
                val clusterWidth = 30
                val clusterCountPerDimension = 10
                val clusterCountPerDimensionHalf = clusterCountPerDimension / 2
                val instancesPerCluster = 1000

                addComponent(ClustersComponent(this@entity).apply {

                    val clusterCenters = (-clusterCountPerDimensionHalf until clusterCountPerDimensionHalf).asSequence().flatMap { x ->
                        (-clusterCountPerDimensionHalf until clusterCountPerDimensionHalf).asSequence().map { y ->
                            Vector3f(x * clusterWidth.toFloat(), 0f, y * clusterWidth.toFloat())
                        }
                    }
                    val clusters = clusterCenters.map { clusterCenter ->
                        Cluster().also {
                            val instances = (0 until instancesPerCluster).map {
                                val instancePosition = Vector3f(Random.nextFloat() - 0.5f, 0f, Random.nextFloat() - 0.5f).mul(clusterWidth.toFloat()).add(clusterCenter)
                                val transformation = Transform().apply {
                                    scale(Vector3f(1f).add(Vector3f(Random.nextFloat() * 0.1f, Random.nextFloat(), Random.nextFloat() * 0.1f)))
                                    rotateLocal(Math.toRadians(180.0 * Random.nextDouble()).toFloat(), 0f, 1f, 0f)
                                    translate(instancePosition)
                                }
                                Instance(this@entity, transformation, spatial = StaticTransformSpatial(transformation, this@entity.getComponent(ModelComponent::class.java)!!))
                            }
                            it.addAll(instances)
                        }
                    }

                    addClusters(clusters.toList())
                })
            }
        }
    }