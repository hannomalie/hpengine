import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer

class InitSibenikKotlin : EngineConsumer {

    override fun consume(engine: Engine<*>) {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sibenik.obj")
        val loaded = LoadModelCommand(modelFile, "sibenik", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        for (entity in loaded.entities) {
            entity.init(engine)
        }
        engine.sceneManager.scene.addAll(loaded.entities)
    }
}
