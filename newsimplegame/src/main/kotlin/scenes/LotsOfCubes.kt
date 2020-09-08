package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.scene.scene
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.StaticTransformSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.TransformSpatial
import org.joml.Vector3f
import java.util.ArrayList
import kotlin.random.Random
var maxDistance = 15
var clusterDistance = 10 * maxDistance
var clusterLocations = arrayOf(
    Vector3f(clusterDistance.toFloat(), 0f, clusterDistance.toFloat()),
    Vector3f(clusterDistance.toFloat(), 0f, (-clusterDistance).toFloat()),
    Vector3f((-clusterDistance).toFloat(), 0f, (-clusterDistance).toFloat()),
    Vector3f(0f, 0f, 0f),
    Vector3f((-clusterDistance).toFloat(), 0f, clusterDistance.toFloat())
)


val Engine.lotsOfCubesScene
    get() = scene("LotsOfCubes") {
        entities {
            entity("Plane") {
                modelComponent(
                    name = "Plane",
                    file = "assets/models/cube.obj",
                    materialManager = scene.materialManager,
                    modelComponentManager = scene.modelComponentManager,
                    gameDirectory = engineContext.config.directories.gameDir
                ).apply {
                    material.materialInfo.put(SimpleMaterial.MAP.DIFFUSE, engineContext.textureManager.getTexture("assets/textures/grass.png", true))
                    material.materialInfo.materialType = SimpleMaterial.MaterialType.FOLIAGE
                }

                val clusters: MutableList<Cluster> = ArrayList()
                val clustersComponent = ClustersComponent(this@entity)
                for (clusterIndex in 0..4) {
                    val cluster = Cluster()
                    val random = java.util.Random()
                    val count = 10
                    for (x in -count until count) {
                        for (y in -count until count) {
                            for (z in -count until count) {
                                val trafo = Transform()
                                val randomFloat = random.nextFloat() - 0.5f
                                trafo.setTranslation(Vector3f().add(Vector3f(clusterLocations[clusterIndex % clusterLocations.size])).add(Vector3f(randomFloat * maxDistance * x, randomFloat * maxDistance * y, randomFloat * maxDistance * z)))
                                val modelComponent = this@entity.getComponent(ModelComponent::class.java)!!
                                val materials = modelComponent.materials
                                cluster.add(Instance(this@entity, trafo, materials, null, StaticTransformSpatial(trafo, modelComponent)))
                            }
                        }
                    }
                    clusters.add(cluster)
                    clustersComponent.addClusters(clusters)
                }
                this@entity.addComponent(clustersComponent)

            }
        }
    }