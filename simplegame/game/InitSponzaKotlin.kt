import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Vector3f
import org.joml.Vector4f
import javax.inject.Inject
import kotlin.random.Random

class InitSponzaKotlin @Inject constructor(val engine: Engine) : EngineConsumer {
    init {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sponza.obj")
        val loaded = LoadModelCommand(modelFile, "sponza", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        engine.sceneManager.addAll(loaded.entities)
        val random = Random.Default
        val randomExtent = 235f
        val randomExtentHalf = randomExtent * 0.5f
        val pointLights = (0..19).map {
            Entity().apply {
                val randomRadius = random.nextFloat()
                val randomY = random.nextFloat()
                val light = PointLight(
                        this,
                        Vector4f(random.nextFloat(), if(randomY > 0f) randomY else 0f, random.nextFloat(), 1f),
                        50f * randomRadius,
                        1f * randomRadius)
                addComponent(light)
                transform.translate(
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
