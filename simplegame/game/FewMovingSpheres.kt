import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.KotlinCompiledComponentLoader
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.instancing.ClustersComponent
import de.hanno.hpengine.engine.materialManager
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector3f
import javax.inject.Inject

class FewMovingSpheres @Inject constructor(engine: Engine) {
    init {
        val entity = Entity("sphere").apply {
            modelComponent(
                StaticModelLoader().load("assets/models/sphere.obj", engine.materialManager, engine.directories.gameDir)
            )
        }

        val clustersComponent = ClustersComponent(entity)
        val instances = (0..499).map { i ->
            val trafo = Transform()
            trafo.rotate(Vector3f(1f, 0f, 0f), -90)
            trafo.setTranslation(Vector3f((100 * i).toFloat(), 0f, 0f))
            Instance(entity, trafo)
        }

        clustersComponent.addInstances(instances)
        entity.addComponent(clustersComponent)
        val codeFile = engine.directories.gameDir.resolve("scripts").resolve("SimpleMoveComponentKotlin.kt")
        val moveComponent = KotlinCompiledComponentLoader.load(engine, codeFile, entity)
        entity.addComponent(moveComponent)

        engine.sceneManager.add(entity)
    }
}
