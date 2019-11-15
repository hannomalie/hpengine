import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class InitSponzaKotlin @Inject constructor(val engine: Engine<*>) : EngineConsumer {
    init {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sponza.obj")
        val loaded = LoadModelCommand(modelFile, "sponza", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        with(engine.sceneManager.scene) {
            runBlocking(engine.singleThreadContext.singleThreadUpdateScope) {
                with(engine.singleThreadContext) {
                    addAll(loaded.entities)
                }
            }
        }
    }
}
