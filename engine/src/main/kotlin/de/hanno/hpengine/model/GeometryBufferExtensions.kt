package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.sizeInBytes
import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.sizeInBytes
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.buffer.vertex.appendIndices
import de.hanno.hpengine.scene.*
import de.hanno.hpengine.toCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger("GeometryBufferExtensions")

fun Model<*>.captureGeometryOffsets(
    currentGeometryOffset: GeometryOffset
): List<GeometryOffset> = when(currentGeometryOffset) {
    is VertexIndexOffsets -> captureVertexAndIndexOffsets(currentGeometryOffset)
    is VertexOffsets -> captureVertexOffsets(currentGeometryOffset)
}
fun Model<*>.captureVertexAndIndexOffsets(vertexIndexOffsets: VertexIndexOffsets): List<VertexIndexOffsets> {
    var currentIndexOffset = vertexIndexOffsets.indexOffset
    var currentVertexOffset = vertexIndexOffsets.vertexOffset

    return meshes.indices.map { i ->
        val mesh = meshes[i] as Mesh<*>
        VertexIndexOffsets(currentVertexOffset, currentIndexOffset).apply {
            currentIndexOffset += ElementCount(mesh.triangles.size * 3)
            currentVertexOffset += ElementCount(mesh.vertices.size)
        }
    }
}
fun Model<*>.captureVertexOffsets(vertexIndexOffsets: VertexOffsets): List<VertexOffsets> {
    var currentVertexOffset = vertexIndexOffsets.vertexOffset

    return meshes.indices.map { i ->
        val mesh = meshes[i] as Mesh<*>
        VertexOffsets(currentVertexOffset).apply {
            currentVertexOffset += mesh.triangleCount * 3
        }
    }
}

fun StaticModel.putToBuffer(
    buffer: GeometryBuffer<VertexStruktPacked>,
    geometryOffset: GeometryOffset = buffer.allocate(this),
): List<GeometryOffset> = synchronized(buffer) {
    logger.debug("Capturing static geometry offsets")
    val geometryOffsetsForMeshes = captureGeometryOffsets(geometryOffset)
    logger.debug("Captured offsets")
    when(buffer) {
        is VertexBuffer -> {
            buffer.vertexStructArray.ensureCapacityInBytes(
                SizeInBytes(VertexStruktPacked.sizeInBytes * (geometryOffset.vertexOffset.value.toInt() + unindexedVertices.size))
            )
            putUnindexedVerticesPacked(buffer.vertexStructArray.typedBuffer, geometryOffset.vertexOffset.value.toInt())
        }
        is VertexIndexBuffer -> {
            geometryOffset as VertexIndexOffsets
            logger.debug("Putting vertices")
            buffer.vertexStructArray.ensureCapacityInBytes(
                SizeInBytes(VertexStruktPacked.sizeInBytes * (geometryOffset.vertexOffset.value.toInt() + indexedVertices.size))
            )
            logger.debug("ensured capacity")
            putVerticesPacked(buffer.vertexStructArray.typedBuffer, geometryOffset.vertexOffset.value.toInt())

            logger.debug("Putting indices")
            buffer.indexBuffer.ensureCapacityInBytes(
                SizeInBytes(VertexStruktPacked.sizeInBytes * (geometryOffset.indexOffset.value.toInt() + indexedVertices.size))
            )
            buffer.indexBuffer.buffer.asIntBuffer().put(
                geometryOffset.indexOffset.value.toInt(), indices, 0, indicesCount.value.toInt()
            )
//            val target = buffer.indexBuffer.buffer.asIntBuffer()
//            geometryOffsetsForMeshes.mapIndexed { index, offset ->
//                offset as VertexIndexOffsets
//                meshes[index].triangles.extractIndices(target, offset.indexOffset.value.toInt())
//            }

//            var currentIndexOffset = geometryOffset.indexOffset.value.toInt()
//            meshes.forEach { mesh ->
//                mesh.triangles.extractIndices(buffer.indexBuffer.buffer, currentIndexOffset)
//                currentIndexOffset += mesh.triangles.size * 3
//            }
        }
    }
    logger.debug("Captured static geometry offsets")
    geometryOffsetsForMeshes
}

fun AnimatedModel.putToBuffer(
    buffer: GeometryBuffer<AnimatedVertexStruktPacked>,
    geometryOffset: GeometryOffset = buffer.allocate(this),
): List<GeometryOffset> = synchronized(buffer) {
    val geometryOffsetsForMeshes = captureGeometryOffsets(geometryOffset)
    when(buffer) {
        is VertexBuffer -> {
            buffer.vertexStructArray.ensureCapacityInBytes(
                SizeInBytes(AnimatedVertexStruktPacked.sizeInBytes * (geometryOffset.vertexOffset.value.toInt() + unindexedVertices.size))
            )
            putUnindexedVerticesPacked(buffer.vertexStructArray.typedBuffer, geometryOffset.vertexOffset.value.toInt())
        }
        is VertexIndexBuffer -> {
            buffer.vertexStructArray.ensureCapacityInBytes(
                SizeInBytes(AnimatedVertexStruktPacked.sizeInBytes * (geometryOffset.vertexOffset.value.toInt() + indexedVertices.size))
            )
            putVerticesPacked(buffer.vertexStructArray.typedBuffer, geometryOffset.vertexOffset.value.toInt())
        }
    }
    logger.info("Captured animated geometry offsets")
    geometryOffsetsForMeshes
}

fun GeometryBuffer<*>.allocate(model: Model<*>): GeometryOffset = when (this) {
    is VertexBuffer<*> -> allocate(model.indicesCount)
    is VertexIndexBuffer<*> -> allocate(model.uniqueVertices.size.toCount(), model.indicesCount)
}
