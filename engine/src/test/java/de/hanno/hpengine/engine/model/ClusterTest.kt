package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.transform.SimpleSpatial
import de.hanno.hpengine.engine.transform.Spatial
import org.joml.Vector3f
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ClusterTest {
    @Test fun testClusterBound() {
        var cluster = Cluster()
        val instance: Instance = Instance(material = null, spatial = object : SimpleSpatial() {
            override val minMaxProperty = arrayOf(Vector3f(-10f, 0f, -10f), Vector3f(0f, 0f, 0f))
        })
        assertArrayEquals(arrayOf(Vector3f(-10f, 0f, -10f), Vector3f(0f, 0f, 0f)), instance.getMinMaxWorld(instance))
        cluster.add(instance)
        cluster.add(Instance(material = null, spatial = object : SimpleSpatial() {
            override val minMaxProperty = arrayOf(Vector3f(5f, 0f, 5f), Vector3f(5f, 0f, 5f))
        }))
        cluster.recalculate()
        assertArrayEquals(arrayOf(Vector3f(-10f, 0f, -10f), Vector3f(5f, 0f, 5f)), cluster.minMax)
    }
}