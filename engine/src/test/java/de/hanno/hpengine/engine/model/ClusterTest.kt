package de.hanno.hpengine.engine.model

import de.hanno.hpengine.TestWithEngine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.SimpleSpatial
import org.joml.Vector3f
import org.junit.Test
import kotlin.test.assertEquals

class ClusterTest: TestWithEngine() {
    @Test fun testClusterBound() {
        var cluster = Cluster()
        val instance = Instance(materials = listOf(), spatial = object : SimpleSpatial() {
            override val minMaxProperty = AABB(Vector3f(-10f, 0f, -10f), Vector3f(0f, 0f, 0f))
        })
        assertEquals(AABB(Vector3f(-10f, 0f, -10f), Vector3f(0f, 0f, 0f)), instance.getMinMaxWorld(instance))
        cluster.add(instance)
        cluster.add(Instance(materials = listOf(), spatial = object : SimpleSpatial() {
            override val minMaxProperty = AABB(Vector3f(5f, 0f, 5f), Vector3f(5f, 0f, 5f))
        }))
        cluster.recalculate()
        assertEquals(AABB(Vector3f(-10f, 0f, -10f), Vector3f(5f, 0f, 5f)), cluster.minMax)
    }
}