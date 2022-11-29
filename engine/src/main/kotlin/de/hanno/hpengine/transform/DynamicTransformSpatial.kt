package de.hanno.hpengine.transform

import de.hanno.hpengine.Transform
import de.hanno.hpengine.model.StaticModel
import org.joml.Matrix4f

class DynamicTransformSpatial(transform: Transform, val model: StaticModel) : TransformSpatial(transform,
    AABB(model.boundingVolume.localAABB)) {
    override fun update(deltaSeconds: Float) = recalculate(transform)
    override fun getBoundingVolume(transform: Matrix4f): AABB = boundingVolume
}