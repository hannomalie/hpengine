import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.KotlinCompiledComponentLoader
import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector3f
import javax.inject.Inject

class FewMovingSpheres @Inject constructor(engine: Engine<*>) {
    init {
        try {
            val loaded = LoadModelCommand(engine.directories.gameDir.resolve("assets/models/sphere.obj"), "sphere", engine.scene.materialManager, engine.directories.gameDir).execute()
            println("loaded entities : " + loaded.entities.size)
            for (current in loaded.entities) {

                val clustersComponent = ClustersComponent(engine, current)
                val instances = (0..499).map { i ->
                    val trafo = SimpleTransform()
                    trafo.rotate(Vector3f(1f, 0f, 0f), -90)
                    trafo.setTranslation(Vector3f((100 * i).toFloat(), 0f, 0f))
                    Instance(current, trafo)
                }

                clustersComponent.addInstances(instances)
                current.addComponent(clustersComponent)
                val codeFile = engine.directories.gameDir.resolve("scripts").resolve("SimpleMoveComponentKotlin.kt")
                val moveComponent = KotlinCompiledComponentLoader.load(engine, codeFile, current)
                current.addComponent(moveComponent, ScriptComponent::class.java as Class<Component>)
            }

            engine.scene.addAll(loaded.entities)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
