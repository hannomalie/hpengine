package scenes

import de.hanno.hpengine.Engine
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.model.Cluster
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.scene.dsl.entity
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.scene.dsl.scene
import de.hanno.hpengine.world.addAnimatedModelEntity
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
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
fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runLotsOfCubes()
}

fun Engine.runLotsOfCubes() {
    val textureManager = systems.firstIsInstance<TextureManagerBaseSystem>()
    world.loadScene {
        addStaticModelEntity("Plane", "assets/models/plane.obj").apply {
            create(MaterialComponent::class.java).apply {
                material = Material(
                    "grass",
                    materialType = Material.MaterialType.FOLIAGE,
                    maps = mutableMapOf(
                        Material.MAP.DIFFUSE to textureManager.getTexture("assets/textures/grass.png", true, config.gameDir)
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
    }
    simulate()
}
