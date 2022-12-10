package de.hanno.hpengine.graphics.state

import com.artemis.Component
import com.artemis.World
import com.artemis.utils.Bag
import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.EntityStrukt
import de.hanno.hpengine.graphics.GpuCommandSync
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.RenderBatch
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentTypedBuffer
import de.hanno.hpengine.graphics.renderer.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.lifecycle.Updatable
import de.hanno.hpengine.model.material.MaterialStrukt
import de.hanno.hpengine.scene.VertexIndexBuffer
import de.hanno.hpengine.Transform
import org.joml.Vector3f

context(GpuContext)
// need dummy for now, because context receiver resolution bug
class RenderState(private val dummy: Unit = Unit) : IRenderState {
    var entityIds: List<Int> = emptyList()
    var componentExtracts: MutableMap<Class<out Component>, List<Component>> = mutableMapOf()
    var componentsForEntities: MutableMap<Int, Bag<Component>> = mutableMapOf()
    val customState = CustomStates()

    var cycle: Long = 0
    val latestDrawResult = DrawResult(FirstPassResult(), SecondPassResult())
    var time = System.currentTimeMillis()
    var deltaSeconds: Float = 0.1f
    override var gpuCommandSync: GpuCommandSync = createCommandSync()

    val gpuHasFinishedUsingIt get() = gpuCommandSync.isSignaled

    fun add(state: Any) = customState.add(state)

    operator fun <T: Any> get(stateRef: StateRef<T>) = customState[stateRef]
    fun <T: Any> set(ref: StateRef<T>, value: T) { customState.set(ref, value) }
}
interface RenderSystem: Updatable {
    val sharedRenderTarget: BackBufferRenderTarget<*>? get() = null
    val requiresClearSharedRenderTarget: Boolean get() = false
    var artemisWorld: World
    fun render(result: DrawResult, renderState: RenderState) { }
    fun renderEditor(result: DrawResult, renderState: RenderState) { }
    fun afterFrameFinished() { }
    fun extract(renderState: RenderState, world: World) { }
}

class CustomStates {
    private val states = mutableListOf<Any>()

    fun add(state: Any) {
        states.add(state)
    }

    operator fun <T: Any> get(ref: StateRef<T>) = states[ref.index] as T
    fun <T: Any> set(ref: StateRef<T>, value: T) { states[ref.index] = value }

    fun clear() = states.clear()
}

class StateRef<out T>(val index: Int)
