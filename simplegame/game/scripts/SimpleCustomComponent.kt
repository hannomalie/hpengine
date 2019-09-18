import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.entity.Entity
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class SimpleCustomComponent @Inject constructor(override val entity: Entity) : CustomComponent {

    override fun CoroutineScope.update(deltaSeconds: Float) {
//        println("Update called in SimpleCustomComponent")
    }
}