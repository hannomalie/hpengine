import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.JavaComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.AnimationController
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.CodeSource
import org.joml.Vector3f
import java.util.ArrayList
import java.util.Random
import javax.inject.Inject

class InitCubeClustersKotlin @Inject constructor(engine: Engine<*>) : Updatable {
    var maxDistance = 15
    var clusterDistance = 10 * maxDistance
    var clusterLocations = arrayOf(Vector3f(clusterDistance.toFloat(), 0f, clusterDistance.toFloat()),
            Vector3f(clusterDistance.toFloat(), 0f, (-clusterDistance).toFloat()),
            Vector3f((-clusterDistance).toFloat(), 0f, (-clusterDistance).toFloat()),
            Vector3f(0f, 0f, 0f),
            Vector3f((-clusterDistance).toFloat(), 0f, clusterDistance.toFloat()))

    init {
        try {
            val loaded = LoadModelCommand(engine.directories.gameDir.resolve("assets/models/cube.obj"), "cube", engine.scene.materialManager, engine.directories.gameDir).execute()
            println("loaded entities : " + loaded.entities.size)
            for (current in loaded.entities) {
                val componentScriptFile = engine.directories.gameDir.resolve("/scripts/SimpleMoveComponent.java")
                current.addComponent(JavaComponent(engine, CodeSource(componentScriptFile), engine.directories.gameDir))
                val clusters: MutableList<Cluster> = ArrayList()
                for (clusterIndex in 0..4) {
                    val cluster = Cluster()
                    val random = Random()
                    val count = 10
                    val clustersComponent = ClustersComponent(current)
                    for (x in -count until count) {
                        for (y in -count until count) {
                            for (z in -count until count) {
                                val trafo: Transform<*> = Transform<Entity>()
                                val randomFloat = random.nextFloat() - 0.5f
                                trafo.setTranslation(Vector3f().add(Vector3f(clusterLocations[clusterIndex % clusterLocations.size])).add(Vector3f(randomFloat * maxDistance * x, randomFloat * maxDistance * y, randomFloat * maxDistance * z)))
                                val modelComponent = current.getComponent(ModelComponent::class.java)
                                val materials = modelComponent?.materials ?: ArrayList()
                                cluster.add(Instance(current, trafo, materials, AnimationController(0, 0f), object : SimpleSpatial() {
                                    override val minMax: AABB
                                        get() = current.minMax
                                }))
                            }
                        }
                    }
                    clusters.add(cluster)
                    clustersComponent.addClusters(clusters)
                    current.addComponent(clustersComponent)
                    println("Added " + cluster.size)
                }
            }
            engine.sceneManager.addAll(loaded.entities)
            Thread.sleep(500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}