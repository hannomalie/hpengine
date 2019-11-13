import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Cluster
import de.hanno.hpengine.engine.model.loader.md5.AnimationController
import de.hanno.hpengine.engine.transform.AnimatedTransformSpatial
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import java.util.ArrayList
import java.util.Random
import javax.inject.Inject

class InitInstancedAnimatedKotlin @Inject constructor(val engine: Engine<*>) {

    var isInitialized: Boolean = false
        private set

    internal var maxDistance = 475
    internal var clusterDistance = 3 * maxDistance
    internal var clusterLocations = arrayOf(Vector3f(clusterDistance.toFloat(), 0f, clusterDistance.toFloat()), Vector3f(clusterDistance.toFloat(), 0f, (-clusterDistance).toFloat()), Vector3f((-clusterDistance).toFloat(), 0f, (-clusterDistance).toFloat()), Vector3f(0f, 0f, 0f), Vector3f((-clusterDistance).toFloat(), 0f, clusterDistance.toFloat()))

    init {
        try {
            loadLotsOfInstances(engine, "assets/models/doom3monster/monster.md5mesh", 1, "hellknight")
//            loadLotsOfInstances(engine, "assets/models/cube.obj", 100, "cube")
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun loadLotsOfInstances(engine: Engine<*>, assetPath: String, scale: Int, name: String) {
        val loaded = LoadModelCommand(engine.directories.gameDir.resolve(assetPath), name, engine.scene.materialManager, engine.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        for (entity in loaded.entities) {
            //                File componentScriptFile = new File(engine.getDirectories().getGameDir() + "/scripts/SimpleMoveComponent.java");
            //                entity.addComponent(new JavaComponent(new CodeSource(componentScriptFile)));
            val clusters = ArrayList<Cluster>()
            val clustersComponent = ClustersComponent(engine, entity)
            for (clusterIndex in 0..2) {
                val cluster = Cluster()
                val random = Random()
                val count = 6
                for (x in -count until count) {
                    for (y in -count until count) {
                        for (z in -count until count) {
                            val trafo = SimpleTransform()
                            trafo.scale(scale.toFloat())
                            val randomFloat = random.nextFloat() - 0.5f
                            trafo.rotate(Vector3f(1f, 0f, 0f), -90)
                            trafo.rotate(Vector3f(0f, 0f, 1f), (random.nextFloat() * 360f).toInt())
                            trafo.setTranslation(Vector3f().add(Vector3f(clusterLocations[clusterIndex % clusterLocations.size])).add(Vector3f(randomFloat * maxDistance.toFloat() * x.toFloat(), 0.001f * randomFloat, randomFloat * maxDistance.toFloat() * z.toFloat())))

                            val modelComponent = entity.getComponent(ModelComponent::class.java)
                            val materials = modelComponent.materials
                            ClustersComponent.addInstance(entity, cluster, trafo, modelComponent, materials, AnimationController(120, 24f), AnimatedTransformSpatial(trafo, modelComponent))
                        }
                    }
                }
                clusters.add(cluster)
                println("Added " + cluster.size)
            }
            clustersComponent.addClusters(clusters)
            entity.addComponent(clustersComponent)
        }

        //        Entity debugCam = new Entity("DebugCam");
        //        loaded.entities.add(debugCam.addComponent(new Camera(debugCam)));
        engine.scene.addAll(loaded.entities)
    }
}
