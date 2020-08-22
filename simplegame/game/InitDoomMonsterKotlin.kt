import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.config
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f
import javax.inject.Inject

class InitDoomMonsterKotlin @Inject constructor(val engine: Engine) : EngineConsumer {

    init {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/doom3monster/monster.md5mesh")
        val loaded = LoadModelCommand(modelFile, "hellknight", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        loaded.entities.first().getComponent(ModelComponent::class.java)!!.spatial.boundingVolume.localAABB = AABBData(
            Vector3f(-60f, -10f, -35f),
            Vector3f(60f, 130f, 50f)
        )
        println("loaded entities : " + loaded.entities.size)

        engine.sceneManager.addAll(loaded.entities)
    }
}
