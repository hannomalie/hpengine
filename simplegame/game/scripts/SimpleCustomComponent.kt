import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.entity.Entity
import kotlinx.coroutines.CoroutineScope

class SimpleCustomComponent(override val entity: Entity) : CustomComponent {

    override fun CoroutineScope.update(deltaSeconds: Float) {
//        println("Update called in SimpleCustomComponent")
    }
}