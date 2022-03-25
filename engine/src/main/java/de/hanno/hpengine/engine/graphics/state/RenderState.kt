package de.hanno.hpengine.engine.graphics.state

import com.artemis.Component
import com.artemis.World
import com.artemis.utils.Bag
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.EntityStrukt
import de.hanno.hpengine.engine.graphics.GpuCommandSync
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentTypedBuffer
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.model.material.MaterialStrukt
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.struct.copyFrom
import org.joml.Vector3f

class RenderState(private val gpuContext: GpuContext<*>) {
    var entityIds: List<Int> = emptyList()
    var componentExtracts: Map<Class<out Component>, List<Component>> = emptyMap()
    var componentsForEntities: MutableMap<Int, Bag<Component>> = mutableMapOf()
    val customState = CustomStates()

    val latestDrawResult = DrawResult(FirstPassResult(), SecondPassResult())

    var time = System.currentTimeMillis()

    val directionalLightState = PersistentMappedStructBuffer(1, gpuContext, { DirectionalLightState() })

    val lightState = LightState(gpuContext)

    val entitiesState: EntitiesState = EntitiesState(gpuContext)

    val environmentProbesState = EnvironmentProbeState(gpuContext)

    var skyBoxMaterialIndex = -1

    var camera = Camera(Entity("RenderStateCameraEntity"), 1280f/720f)
    var pointLightMovedInCycle: Long = 0
    var directionalLightHasMovedInCycle: Long = 0
    var sceneMin = Vector3f()
    var sceneMax = Vector3f()

    var cycle: Long = 0
    var gpuCommandSync: GpuCommandSync = object : GpuCommandSync {}

    val renderBatchesStatic: List<RenderBatch>
        get() = entitiesState.renderBatchesStatic
    val renderBatchesAnimated: List<RenderBatch>
        get() = entitiesState.renderBatchesAnimated

    val vertexIndexBufferStatic: VertexIndexBuffer
        get() = entitiesState.vertexIndexBufferStatic
    val vertexIndexBufferAnimated: VertexIndexBuffer
        get() = entitiesState.vertexIndexBufferAnimated

    val entitiesBuffer: PersistentTypedBuffer<EntityStrukt>
        get() = entitiesState.entitiesBuffer

    val materialBuffer: PersistentTypedBuffer<MaterialStrukt>
        get() = entitiesState.materialBuffer

    var deltaSeconds: Float = 0.1f

    constructor(source: RenderState) : this(source.gpuContext) {
        entitiesState.vertexIndexBufferStatic = source.entitiesState.vertexIndexBufferStatic
        entitiesState.vertexIndexBufferAnimated = source.entitiesState.vertexIndexBufferAnimated
        camera.init(source.camera)
        directionalLightState[0].copyFrom(source.directionalLightState[0])
        lightState.pointLights = source.lightState.pointLights
        lightState.pointLightBuffer = source.lightState.pointLightBuffer
        lightState.areaLights = source.lightState.areaLights
        lightState.tubeLights = source.lightState.tubeLights
        lightState.pointLightShadowMapStrategy = source.lightState.pointLightShadowMapStrategy
        lightState.areaLightDepthMaps = source.lightState.areaLightDepthMaps
        entitiesState.entityMovedInCycle = source.entitiesState.entityMovedInCycle
        entitiesState.staticEntityMovedInCycle = source.entitiesState.staticEntityMovedInCycle
        entitiesState.entityAddedInCycle = source.entitiesState.entityAddedInCycle
        environmentProbesState.environmapsArray3Id = source.environmentProbesState.environmapsArray3Id
        environmentProbesState.environmapsArray0Id = source.environmentProbesState.environmapsArray0Id
        environmentProbesState.activeProbeCount = source.environmentProbesState.activeProbeCount
        environmentProbesState.environmentMapMin = source.environmentProbesState.environmentMapMin
        environmentProbesState.environmentMapMax = source.environmentProbesState.environmentMapMax
        environmentProbesState.environmentMapWeights = source.environmentProbesState.environmentMapWeights
        skyBoxMaterialIndex = source.skyBoxMaterialIndex
        directionalLightHasMovedInCycle = source.directionalLightHasMovedInCycle
        pointLightMovedInCycle = source.pointLightMovedInCycle
        sceneMin = source.sceneMin
        sceneMax = source.sceneMax
        latestDrawResult.set(latestDrawResult)
        entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic)
        entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated)
        componentExtracts = HashMap(source.componentExtracts)
        componentsForEntities = HashMap(source.componentsForEntities)
        entityIds = ArrayList(source.entityIds)
    }
    val gpuHasFinishedUsingIt
        get() = gpuCommandSync.isSignaled
    fun addStatic(batch: RenderBatch) {
        entitiesState.renderBatchesStatic.add(batch)
    }

    fun addAnimated(batch: RenderBatch) {
        entitiesState.renderBatchesAnimated.add(batch)
    }

    fun add(state: Any) = customState.add(state)

    operator fun <T> get(stateRef: StateRef<T>) = customState[stateRef]

    operator fun <T: Component> get(clazz: Class<T>): Bag<T> = componentExtracts[clazz] as Bag<T>

}
interface RenderSystem: Updatable {
    val sharedRenderTarget: RenderTarget<*>?
        get() = null
    val requiresClearSharedRenderTarget: Boolean
        get() = false
    var artemisWorld: World
    fun render(result: DrawResult, renderState: RenderState) { }
    fun renderEditor(result: DrawResult, renderState: RenderState) { }
    fun afterFrameFinished() { }
    fun extract(scene: Scene, renderState: RenderState, world: World) { }
    fun beforeSetScene(nextScene: Scene) { }
    fun afterSetScene(currentScene: Scene) {}
}

class CustomStates {
    private val states = mutableListOf<Any>()
    fun add(state: Any) {
        states.add(state)
    }

    operator fun <T> get(ref: StateRef<T>) = states[ref.index] as T

    fun clear() = states.clear()
}

class StateRef<out T>(val index: Int)
