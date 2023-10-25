package de.hanno.hpengine.math

import de.hanno.hpengine.transform.w
import de.hanno.hpengine.transform.x
import de.hanno.hpengine.transform.y
import de.hanno.hpengine.transform.z
import org.joml.Vector2fc
import org.joml.Vector3fc
import org.joml.Vector4fc
import struktgen.api.Strukt
import java.nio.ByteBuffer

// TODO: As soon as i remove this one, nothing works anymore and objects are rendered
// on a flat plane, wrong transformation or anything. I need to figureout why that happens
// since strukt codegen for this interface is exactly the same as for the one in the api package
interface Vector4fStrukt : Strukt {
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float
    context(ByteBuffer) var z: Float
    context(ByteBuffer) var w: Float

    context(ByteBuffer) fun set(target: Vector4fc) {
        x = target.x
        y = target.y
        z = target.z
        w = target.w
    }
    context(ByteBuffer) fun set(target: Vector3fc) {
        x = target.x
        y = target.y
        z = target.z
        w = 1.0f
    }
    context(ByteBuffer) fun set(target: Vector2fc) {
        x = target.x()
        y = target.y()
    }

    companion object
}
