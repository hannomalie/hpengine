package de.hanno.hpengine.scene

import com.google.common.collect.ImmutableSet
import de.hanno.hpengine.vertexbuffer.DataChannelComponent.*
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Vector4i

data class AnimatedVertex (val position: Vector3f = Vector3f(),
                  val texCoord: Vector2f = Vector2f(),
                  val normal: Vector3f = Vector3f(),
                  val weights: Vector4f,
                  val jointIndices: Vector4i) {

    companion object {

        val channels = ImmutableSet.of(
            FloatThree("position", "vec3"),
            FloatTwo("texCoord", "vec2"),
            FloatThree("normal", "vec3"),
            FloatFour("weights", "vec4"),
            IntFour("jointIndices", "ivec4")
        )

        val sizeInBytes = channels.map { it.byteSize }.reduce { a, b -> a + b }
    }
}