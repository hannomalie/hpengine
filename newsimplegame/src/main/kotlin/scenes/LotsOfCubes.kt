package scenes

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f

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
    get() = de.hanno.hpengine.engine.scene.dsl.scene("Hellknight") {
        entity("Plane") {
            add(
                StaticModelComponentDescription(
                    file = "assets/models/cube.obj",
                    Directory.Game,
                    AABBData(
                        Vector3f(-60f, -10f, -35f),
                        Vector3f(60f, 130f, 50f)
                    ),
//                    TODO: Reimplement
//                    material = Material(
//                        "grass",
//                        materialType = Material.MaterialType.FOLIAGE,
//                        maps = mutableMapOf(
//                            Material.MAP.DIFFUSE to
//                                    application.koin.get<TextureManager>()
//                                        .getTexture("assets/textures/grass.png", true)
//                        )
//                    )
                )
            )
        }
    }
//        val clusters: MutableList<Cluster> = ArrayList()
//        val clustersComponent = ClustersComponent(this@entity)
//        for (clusterIndex in 0..4) {
//            val cluster = Cluster()
//            val random = java.util.Random()
//            val count = 10
//            for (x in -count until count) {
//                for (y in -count until count) {
//                    for (z in -count until count) {
//                        val trafo = Transform()
//                        val randomFloat = random.nextFloat() - 0.5f
//                        trafo.setTranslation(Vector3f().add(Vector3f(clusterLocations[clusterIndex % clusterLocations.size])).add(Vector3f(randomFloat * maxDistance * x, randomFloat * maxDistance * y, randomFloat * maxDistance * z)))
//                        val modelComponent = this@entity.getComponent(ModelComponent::class.java)!!
//                        val materials = modelComponent.materials
//                        cluster.add(Instance(this@entity, trafo, materials, null, StaticTransformSpatial(trafo, modelComponent)))
//                    }
//                }
//            }
//            clusters.add(cluster)
//            clustersComponent.addClusters(clusters)
//        }
//        this@entity.addComponent(clustersComponent)
