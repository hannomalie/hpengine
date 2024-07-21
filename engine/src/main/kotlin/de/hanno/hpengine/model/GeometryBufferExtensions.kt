package de.hanno.hpengine.model

import AnimatedVertexStruktPackedImpl.Companion.type
import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.ElementCount
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.buffer.vertex.appendIndices
import de.hanno.hpengine.scene.*
import de.hanno.hpengine.toCount


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
            currentIndexOffset += ElementCount((mesh.indexBufferValues.capacity() / Integer.BYTES))
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
    val geometryOffsetsForMeshes = captureGeometryOffsets(geometryOffset)
    val vertices = when(buffer) {
        is VertexBuffer ->  unindexedVerticesPacked
        is VertexIndexBuffer -> verticesPacked
    }
    buffer.vertexStructArray.addAll(
        geometryOffset.vertexOffset * SizeInBytes(VertexStruktPacked.type.sizeInBytes),
        vertices.byteBuffer
    )
    when (geometryOffset) {
        is VertexIndexOffsets -> (buffer as VertexIndexBuffer<*>).indexBuffer.appendIndices(
            geometryOffset.indexOffset,
            this.indices
        )

        is VertexOffsets -> {}
    }
    geometryOffsetsForMeshes
}

fun AnimatedModel.putToBuffer(
    buffer: GeometryBuffer<AnimatedVertexStruktPacked>,
    geometryOffset: GeometryOffset = buffer.allocate(this),
): List<GeometryOffset> = synchronized(buffer) {
    val geometryOffsetsForMeshes = captureGeometryOffsets(geometryOffset)

    val vertices = when(buffer) {
        is VertexBuffer ->  unindexedVerticesPacked
        is VertexIndexBuffer -> verticesPacked
    }
    buffer.vertexStructArray.addAll(
        geometryOffset.vertexOffset * SizeInBytes(AnimatedVertexStruktPacked.type.sizeInBytes),
        vertices.byteBuffer
    )
    when (geometryOffset) {
        is VertexIndexOffsets -> (buffer as VertexIndexBuffer<*>).indexBuffer.appendIndices(
            geometryOffset.indexOffset,
            indices
        )

        is VertexOffsets -> {}
    }
    geometryOffsetsForMeshes
}

fun GeometryBuffer<*>.allocate(model: Model<*>): GeometryOffset = when (this) {
    is VertexBuffer<*> -> allocate(model.indicesCount)
    is VertexIndexBuffer<*> -> allocate(model.uniqueVertices.size.toCount(), model.indicesCount)
}
