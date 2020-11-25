import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.materialManager
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f
import javax.inject.Inject

class InitDoomMonsterKotlin @Inject constructor(val engine: Engine) : EngineConsumer {
    init {
        val entity = Entity("hellknight").apply {
            modelComponent(
                    AnimatedModelLoader().load("assets/models/doom3monster/monster.md5mesh", engine.materialManager, engine.directories.gameDir)
            ).apply {
                spatial.boundingVolume.localAABB = AABBData(
                        Vector3f(-60f, -10f, -35f),
                        Vector3f(60f, 130f, 50f)
                )
            }
        }
        engine.sceneManager.add(entity)
    }
}
