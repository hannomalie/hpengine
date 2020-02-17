import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import org.joml.Vector3f
import org.joml.Vector4f
import javax.inject.Inject
import kotlin.random.Random

class InitSponzaKotlin @Inject constructor(val engine: Engine<*>) : EngineConsumer {
    init {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/bpcem_playground.obj")
        val loaded = LoadModelCommand(modelFile, "sponza", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        engine.sceneManager.addAll(loaded.entities)
        val random = Random.Default
        val randomExtent = 15f
        val randomExtentHalf = randomExtent * 0.5f
        val pointLights = (0..99).map {
            Entity().apply {
                addComponent(PointLight(this, Vector4f(random.nextFloat(), random.nextFloat(), random.nextFloat(), 1f), 10f*random.nextFloat()))
                translate(
                    Vector3f(
                        randomExtent*random.nextFloat() - randomExtentHalf,
                        randomExtent*random.nextFloat() - randomExtentHalf,
                        randomExtent*random.nextFloat() - randomExtentHalf
                    )
                )
            }
        }
        engine.sceneManager.addAll(pointLights)
    }
}
