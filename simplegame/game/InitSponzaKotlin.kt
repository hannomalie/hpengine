import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer

class InitSponzaKotlin : EngineConsumer {

    var isInitialized: Boolean = false
        private set

    override fun consume(engine: Engine<*>) {

        try {
            val modelFile = Config.getInstance().directoryManager.gameDir.resolve("assets/models/sponza.obj")
            val loaded = LoadModelCommand(modelFile, "sponza", engine.scene.materialManager).execute()
            println("loaded entities : " + loaded.entities.size)
            for (entity in loaded.entities) {
                entity.init(engine)
            }
            engine.sceneManager.scene.addAll(loaded.entities)

            //            Entity entity = engine.getSceneManager().getSimpleScene().getEntityManager().create();
            //            entity.addComponent(new Camera(entity));
            //            engine.getSceneManager().getSimpleScene().add(entity);
            Thread.sleep(500)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
