import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class SimpleCustomComponent @Inject constructor(override val entity: Entity) : CustomComponent {

    override fun CoroutineScope.update(scene: Scene, deltaSeconds: Float) {
//        println("Update called in SimpleCustomComponent")
    }
}