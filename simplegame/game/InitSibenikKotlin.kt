import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.KotlinComponentLoader
import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer

class InitSibenikKotlin : EngineConsumer {

    override fun consume(engine: Engine<*>) {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sibenik.obj")
        val loaded = LoadModelCommand(modelFile, "sibenik", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        for (entity in loaded.entities) {
            val codeFile = engine.directories.gameDir.resolve("scripts").resolve("SimpleCustomComponent.kt")
            val component = KotlinComponentLoader.load(engine, codeFile)
            println("Loaded $component")
            entity.addComponent(component, ScriptComponent::class.java as Class<Component>)
            entity.init(engine)
        }
        engine.sceneManager.scene.addAll(loaded.entities)
    }
}
