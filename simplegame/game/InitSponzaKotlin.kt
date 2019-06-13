import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer

class InitSponzaKotlin : EngineConsumer {

    override fun consume(engine: Engine<*>) {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sponza.obj")
        val loaded = LoadModelCommand(modelFile, "sponza", engine.scene.materialManager).execute()
        println("loaded entities : " + loaded.entities.size)
        for (entity in loaded.entities) {
            entity.init(engine)
        }
        engine.sceneManager.scene.addAll(loaded.entities)
    }
}
