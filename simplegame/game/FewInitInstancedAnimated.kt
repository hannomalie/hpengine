import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import javax.inject.Inject

class FewInitInstancedAnimated @Inject constructor(engine: Engine<*>) {
    init {
        try {
            val loaded = LoadModelCommand(engine.directories.gameDir.resolve("assets/models/doom3monster/monster.md5mesh"), "hellknight", engine.scene.materialManager, engine.directories.gameDir).execute()
            println("loaded entities : " + loaded.entities.size)
            for (current in loaded.entities) {

                val clustersComponent = ClustersComponent(engine, current)
                val instances = (0..299).map { i ->
                    val trafo = SimpleTransform()
                    trafo.rotate(Vector3f(1f, 0f, 0f), -90)
                    trafo.setTranslation(Vector3f((100 * i).toFloat(), 0f, 0f))
                    Instance(current, trafo)
                }

                clustersComponent.addInstances(instances)
            }

            engine.scene.addAll(loaded.entities)

            engine.scene.add(Entity().apply { addComponent(Camera(this)) })
            Thread.sleep(500)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
