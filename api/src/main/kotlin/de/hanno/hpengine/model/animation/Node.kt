package de.hanno.hpengine.model.animation

import org.joml.Matrix4f
import java.util.ArrayList

class Node(val name: String, val parent: Node?) {
    private val children: MutableList<Node>
    private val transformations: MutableList<Matrix4f>
    fun addChild(node: Node) {
        children.add(node)
    }

    fun addTransformation(transformation: Matrix4f) {
        transformations.add(transformation)
    }

    fun findByName(targetName: String): Node? {
        var result: Node? = null
        if (name == targetName) {
            result = this
        } else {
            for (child in children) {
                result = child.findByName(targetName)
                if (result != null) {
                    break
                }
            }
        }
        return result
    }

    val animationFrames: Int
        get() {
            var numFrames = transformations.size
            for (child in children) {
                val childFrame = child.animationFrames
                numFrames = Math.max(numFrames, childFrame)
            }
            return numFrames
        }

    fun getChildren(): List<Node> {
        return children
    }

    fun getTransformations(): List<Matrix4f> {
        return transformations
    }

    companion object {
        fun getParentTransforms(node: Node?, framePos: Int): Matrix4f {
            return if (node == null) {
                Matrix4f()
            } else {
                val parentTransform = Matrix4f(getParentTransforms(node.parent, framePos))
                val transformations = node.getTransformations()
                val nodeTransform: Matrix4f
                val transfSize = transformations.size
                nodeTransform = if (framePos < transfSize) {
                    transformations[framePos]
                } else if (transfSize > 0) {
                    transformations[transfSize - 1]
                } else {
                    Matrix4f()
                }
                parentTransform.mul(nodeTransform)
            }
        }
    }

    init {
        transformations = ArrayList()
        children = ArrayList()
    }
}