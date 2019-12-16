import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.KotlinCompiledComponentLoader
import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import javax.inject.Inject

class InitSibenikKotlin @Inject constructor(val engine: Engine<*>) : EngineConsumer {

    init {
        val modelFile = engine.config.directories.gameDir.resolve("assets/models/sibenik.obj")
        val loaded = LoadModelCommand(modelFile, "sibenik", engine.scene.materialManager, engine.config.directories.gameDir).execute()
        println("loaded entities : " + loaded.entities.size)
        for (entity in loaded.entities) {
            val codeFile = engine.directories.gameDir.resolve("scripts").resolve("SimpleCustomComponent.kt")
            val component = KotlinCompiledComponentLoader.load(engine, codeFile, Entity())
            println("Loaded $component")
            entity.addComponent(component, ScriptComponent::class.java as Class<Component>)
        }

        engine.sceneManager.addAll(loaded.entities)
    }
}
