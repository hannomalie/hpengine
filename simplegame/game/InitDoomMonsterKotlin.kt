import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent.Companion.modelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.lifecycle.EngineConsumer
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader
import de.hanno.hpengine.engine.transform.AABBData
import org.joml.Vector3f
import javax.inject.Inject

class InitDoomMonsterKotlin @Inject constructor(val engine: Engine) : EngineConsumer {
    init {
        val entity = Entity("hellknight").apply {
            modelComponent(
                AnimatedModelLoader().load(
                    "assets/models/doom3monster/monster.md5mesh",
                    engine.engineContext.backend.textureManager,
                    engine.directories.gameDir
                )
            ).apply {
                spatial.boundingVolume.localAABB = AABBData(
                    Vector3f(-60f, -10f, -35f),
                    Vector3f(60f, 130f, 50f)
                )
            }
        }
        engine.sceneManager.scene.addAll(listOf(entity))
    }
}
