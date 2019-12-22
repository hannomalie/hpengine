package de.hanno.hpengine.editor.input

import org.joml.Vector3f

interface EditorInputConfig {
    var constraintAxis: AxisConstraint
    var transformMode: TransformMode
    var transformSpace: TransformSpace
    var selectionMode: SelectionMode
    var rotateAround: RotateAround
}

class EditorInputConfigImpl(
        override var constraintAxis: AxisConstraint = AxisConstraint.None,
        override var transformMode: TransformMode = TransformMode.Translate,
        override var transformSpace: TransformSpace = TransformSpace.World,
        override var selectionMode: SelectionMode = SelectionMode.Entity,
        override var rotateAround: RotateAround = RotateAround.Self
): EditorInputConfig

enum class RotateAround {
    Self,
    Pivot
}
enum class AxisConstraint(val axis: Vector3f) {
    None(Vector3f()),
    X(Vector3f(1f, 0f, 0f)),
    Y(Vector3f(0f, 1f, 0f)),
    Z(Vector3f(0f, 0f, 1f))
}

enum class TransformMode {
    None, Translate, Rotate, Scale
}

enum class TransformSpace {
    World, Local, View
}

enum class SelectionMode {
    Entity, Mesh
}