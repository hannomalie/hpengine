package de.hanno.hpengine.transform

import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4fc

inline val Vector3fc.x get() = x()
inline val Vector3fc.y get() = y()
inline val Vector3fc.z get() = z()

inline var Vector3f.x
    get() = x()
    set(value) {
        x = value
    }
inline var Vector3f.y
    get() = y()
    set(value) {
        y = value
    }
inline var Vector3f.z
    get() = z()
    set(value) {
        z = value
    }

inline val Vector4fc.x
    get() = x()
inline val Vector4fc.y
    get() = y()
inline val Vector4fc.z
    get() = z()
inline val Vector4fc.w
    get() = w()
