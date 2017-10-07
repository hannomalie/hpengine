package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.lifecycle.LifeCycle

import java.util.ArrayList

class Cluster : ArrayList<Instance>(), LifeCycle, Spatial by SimpleSpatial() {

    override fun isInitialized() = true

    override fun update(seconds: Float) {
        for (i in 0..size - 1) {
            get(i).update(seconds)
        }
    }

    var isHasMoved: Boolean
        get() {
            for (i in 0..size - 1) {
                if (get(i).isHasMoved) {
                    return true
                }
            }
            return false
        }
        set(value) {
            for (i in 0..size - 1) {
                get(i).isHasMoved = value
            }
        }
}
