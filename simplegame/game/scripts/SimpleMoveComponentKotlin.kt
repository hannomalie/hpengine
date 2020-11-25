//package scripts

import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.instancing.instances
import de.hanno.hpengine.engine.model.Instance
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import javax.inject.Inject
import kotlin.random.Random

class SimpleMoveComponentKotlin @Inject constructor(override val entity: Entity) : CustomComponent {
    val randoms = entity.instances.map { Random.nextFloat() }
    val reversedRandoms = randoms.reversed()
    val lifeTimes = entity.instances.map { 0f }.toFloatArray()

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        for((index, instance) in entity.instances.withIndex()) {
            with(ParticleSystem) {
                val lifeTime = lifeTimes[index]
                instance.update(randoms[index], reversedRandoms[index], lifeTime, deltaSeconds)
                lifeTimes[index] += deltaSeconds
                if(this.getMaxLifeTime(randoms[index]) > this.maxLifeTime) {
                    lifeTimes[index] = 0f
                }
            }
        }
    }

    companion object ParticleSystem {
        val maxLifeTime = 5f
        fun getMaxLifeTime(random: Float) = maxLifeTime * (1+random)

        fun Instance.update(random: Float, randomSpeed: Float, lifeTime: Float, deltaSeconds: Float) {
            val amountY = 10f * Math.max(0.5f, randomSpeed) * random * deltaSeconds
            val amountX = 3f * randomSpeed * (random-0.5f) * deltaSeconds
            val amountZ = 2f * randomSpeed * (random-0.5f) * deltaSeconds
            transform.translate(amountX, amountY, amountZ)
            val alive = lifeTime < maxLifeTime
            if(alive) {
                if(transform.getScale(Vector3f()).x < 2f) {
                    transform.scaleAround(1.005f, 0f, 0f, 0f)
                }
            }
            reset(alive)
        }
        fun Instance.reset(alive: Boolean) = transform.run {
            val maxY = 50
            val max = 10
            val min = -max
            if(position.x > max || position.y > maxY || position.z > max ||
                position.x < min || position.y < 0 || position.z < min || !alive) {
                transformation.scaleAround(.005f, 0f, 0f, 0f)
                translation(0f, 0f, 0f)
            }
        }
    }
}