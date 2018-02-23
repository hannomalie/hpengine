package de.hanno.hpengine.engine.graphics.state

import de.hanno.hpengine.engine.BufferableMatrix4f
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer
import de.hanno.hpengine.engine.graphics.renderer.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.CommandOrganization
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.AnimatedVertex
import de.hanno.hpengine.engine.scene.Vertex
import de.hanno.hpengine.engine.scene.VertexIndexBuffer
import de.hanno.hpengine.util.Util
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.FloatBuffer
import java.util.*

class RenderState(gpuContext: GpuContext) {
    private val gpuContext: GpuContext = gpuContext

    val latestDrawResult = DrawResult(FirstPassResult(), SecondPassResult())

    val directionalLightState = DirectionalLightState()
    val entitiesState: EntitiesState = EntitiesState(gpuContext)

    val commandOrganizationStatic: CommandOrganization = CommandOrganization(gpuContext)
    val commandOrganizationAnimated: CommandOrganization = CommandOrganization(gpuContext)

    var camera = Camera(Entity())
    var pointlightMovedInCycle: Long = 0
    var directionalLightHasMovedInCycle: Long = 0
    var sceneInitiallyDrawn: Boolean = false
    var sceneMin = Vector4f()
    var sceneMax = Vector4f()
    private var pipelines: MutableList<Pipeline> = ArrayList()

    var cycle: Long = 0
    @Volatile //        glFlush();
    var gpuCommandSync: Long = 0

    val renderBatchesStatic: List<RenderBatch>
        get() = entitiesState.renderBatchesStatic
    val renderBatchesAnimated: List<RenderBatch>
        get() = entitiesState.renderBatchesAnimated

    val vertexIndexBufferStatic: VertexIndexBuffer<*>
        get() = entitiesState.vertexIndexBufferStatic
    val vertexIndexBufferAnimated: VertexIndexBuffer<*>
        get() = entitiesState.vertexIndexBufferAnimated

    val entitiesBuffer: GPUBuffer<*>
        get() = entitiesState.entitiesBuffer

    val materialBuffer: GPUBuffer<*>
        get() = entitiesState.materialBuffer

    val directionalLightViewMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightViewMatrixAsBuffer

    val directionalLightProjectionMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightProjectionMatrixAsBuffer

    val directionalLightViewProjectionMatrixAsBuffer: FloatBuffer
        get() = directionalLightState.directionalLightViewProjectionMatrixAsBuffer

    /**
     * Copy constructor
     * @param source
     */
    constructor(source: RenderState) : this(source.gpuContext) {
        init(source.entitiesState.vertexIndexBufferStatic, source.entitiesState.vertexIndexBufferAnimated, source.entitiesState.joints, source.camera, source.entitiesState.entityMovedInCycle, source.directionalLightHasMovedInCycle, source.pointlightMovedInCycle, source.sceneInitiallyDrawn, source.sceneMin, source.sceneMax, source.cycle, source.directionalLightState.directionalLightViewMatrixAsBuffer, source.directionalLightState.directionalLightProjectionMatrixAsBuffer, source.directionalLightState.directionalLightViewProjectionMatrixAsBuffer, source.directionalLightState.directionalLightScatterFactor, source.directionalLightState.directionalLightDirection, source.directionalLightState.directionalLightColor, source.entitiesState.entityAddedInCycle)
        this.entitiesState.renderBatchesStatic.addAll(source.entitiesState.renderBatchesStatic)
        this.entitiesState.renderBatchesAnimated.addAll(source.entitiesState.renderBatchesAnimated)
        //        TODO: This could be problematic. Copies all buffer contents to the copy's buffers
        //        this.entitiesState.entitiesBuffer.putValues(source.entitiesState.entitiesBuffer.getValuesAsFloats());
        //        this.entitiesState.materialBuffer.putValues(source.entitiesState.materialBuffer.getValuesAsFloats());
        //        this.entitiesState.vertexBuffer.putValues(source.getVertexBuffer().getValuesAsFloats());
        //        this.entitiesState.indexBuffer.put(source.getIndexBuffer().getValues());
    }

    fun init(vertexIndexBufferStatic: VertexIndexBuffer<Vertex>, vertexIndexBufferAnimated: VertexIndexBuffer<AnimatedVertex>, joints: List<BufferableMatrix4f>, camera: Camera, entityMovedInCycle: Long, directionalLightHasMovedInCycle: Long, pointLightMovedInCycle: Long, sceneInitiallyDrawn: Boolean, sceneMin: Vector4f, sceneMax: Vector4f, cycle: Long, directionalLightViewMatrixAsBuffer: FloatBuffer, directionalLightProjectionMatrixAsBuffer: FloatBuffer, directionalLightViewProjectionMatrixAsBuffer: FloatBuffer, directionalLightScatterFactor: Float, directionalLightDirection: Vector3f, directionalLightColor: Vector3f, entityAddedInCycle: Long) {
        this.entitiesState.vertexIndexBufferStatic = vertexIndexBufferStatic
        this.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated
        bufferJoints(joints)
        this.entitiesState.joints = joints // TODO: Fixme
        this.camera.init(camera)
        this.directionalLightState.directionalLightViewMatrixAsBuffer = directionalLightViewMatrixAsBuffer
        this.directionalLightState.directionalLightViewMatrixAsBuffer.rewind()
        this.directionalLightState.directionalLightProjectionMatrixAsBuffer = directionalLightProjectionMatrixAsBuffer
        this.directionalLightState.directionalLightProjectionMatrixAsBuffer.rewind()
        this.directionalLightState.directionalLightViewProjectionMatrixAsBuffer = directionalLightViewProjectionMatrixAsBuffer
        this.directionalLightState.directionalLightViewProjectionMatrixAsBuffer.rewind()
        this.directionalLightState.directionalLightDirection.set(directionalLightDirection)
        this.directionalLightState.directionalLightColor.set(directionalLightColor)
        this.directionalLightState.directionalLightScatterFactor = directionalLightScatterFactor

        this.entitiesState.entityMovedInCycle = entityMovedInCycle
        this.entitiesState.entityAddedInCycle = entityAddedInCycle
        this.directionalLightHasMovedInCycle = directionalLightHasMovedInCycle
        this.pointlightMovedInCycle = pointLightMovedInCycle
        this.sceneInitiallyDrawn = sceneInitiallyDrawn
        this.sceneMin = sceneMin
        this.sceneMax = sceneMax
        this.entitiesState.renderBatchesStatic.clear()
        this.entitiesState.renderBatchesAnimated.clear()
        this.latestDrawResult.set(latestDrawResult)
        //        this.cycle = cycle;
    }

    fun bufferEntities(modelComponents: List<ModelComponent>) {
        entitiesState.entitiesBuffer.put(*Util.toArray(modelComponents, ModelComponent::class.java))
        entitiesState.entitiesBuffer.buffer.position(0)

        //        for(ModelComponent modelComponent: modelComponents) {
        //            for(int i = 0; i < ModelComponentSystemKt.getInstanceCount(modelComponent.getEntity()); i++) {
        //                for(Mesh mesh: modelComponent.getMeshes()) {
        //                    System.out.println("Entity " + modelComponent.getEntity().getName() + " - Mesh " + mesh.getName() + " - Instance " + i);
        //                    ModelComponent.debugPrintFromBufferStatic(entitiesState.entitiesBuffer.getBuffer());
        //                }
        //            }
        //        }
        entitiesState.entitiesBuffer.buffer.position(0)

    }

    fun bufferJoints(joints: List<BufferableMatrix4f>) {
        entitiesState.jointsBuffer.put(*Util.toArray(joints, BufferableMatrix4f::class.java))
    }

    //    TODO: Reimplement this
    //    public void bufferMaterial(Material materials) {
    //        GpuContext.getInstance().execute(() -> {
    //            ArrayList<Material> materials = new ArrayList<>(MaterialManager.getInstance().getMaterials());
    //
    //            int offset = materials.getElementsPerObject() * materials.indexOf(materials);
    //            entitiesState.materialBuffer.put(offset, materials);
    //        });
    //    }

    fun bufferMaterials(engine: Engine) {
        engine.gpuContext.execute {
            val materials = engine.materialManager.materials
            entitiesState.materialBuffer.put(*Util.toArray(materials, Material::class.java))
            entitiesState.materialBuffer.buffer.position(0)
            //            for(Material material: materials) {
            //                System.out.println("Material: " + material.getName() + "(" + material.getMaterialIndex() + ")");
            //                Material.debugPrintFromBufferStatic(entitiesState.materialBuffer.getBuffer());
            //            }
        }
    }

    fun addStatic(batch: RenderBatch) {
        entitiesState.renderBatchesStatic.add(batch)
    }

    fun addAnimated(batch: RenderBatch) {
        entitiesState.renderBatchesAnimated.add(batch)
    }

    fun preventSwap(currentStaging: RenderState, currentRead: RenderState): Boolean {
        return currentStaging.cycle < currentRead.cycle
    }

    fun addPipeline(pipeline: Pipeline): Int {
        return if (pipelines.add(pipeline)) {
            pipelines.indexOf(pipeline)
        } else {
            throw IllegalArgumentException("GPUFrustumCulledPipeline could somehow not be added to state")
        }
    }

    operator fun <T : Pipeline> get(index: TripleBuffer.PipelineRef<T>): T {
        return pipelines[index.index] as T
    }

    fun getPipelines(): List<Pipeline> {
        return pipelines
    }

    fun setVertexIndexBufferStatic(vertexIndexBuffer: VertexIndexBuffer<Vertex>) {
        this.entitiesState.vertexIndexBufferStatic = vertexIndexBuffer
    }

    fun setVertexIndexBufferAnimated(vertexIndexBufferAnimated: VertexIndexBuffer<AnimatedVertex>) {
        this.entitiesState.vertexIndexBufferAnimated = vertexIndexBufferAnimated
    }
}
