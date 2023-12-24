package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.editor.panels.PanelLayout
import de.hanno.hpengine.transform.AABB
import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Operation
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import java.nio.FloatBuffer
import java.util.*

private const val camDistance = 8
private const val showDebugPanel: Boolean = false

enum class Space(val imGuiValue: Int) {
    Local(0),
    World(1),
}
class GizmoSystem(val input: EditorInput) {
    private val objectMatrix = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
    private val objectMatrixBefore = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    private val viewManipulationSize = floatArrayOf(128f, 128f)

    private val empty = floatArrayOf(0f)

    private val viewMatrix = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
    private var projectionMatrix = floatArrayOf(
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f
    )

    private val bounds = floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
    private val boundsSnap = floatArrayOf(1f, 1f, 1f)

    private val snapValue = floatArrayOf(1f, 1f, 1f)
    private val translation = FloatArray(3)
    private val scale = FloatArray(3)
    private val rotation = FloatArray(3)

    private val showBoundingSize = ImBoolean(false)
    private val useSnap = ImBoolean(false)

    private var currentSpace = Space.Local
    private var currentGizmoOperation = Operation.TRANSLATE // TODO: Extract enum for operations

    private val tempFloat = ImFloat()

    private var boundSizingSnap = false

    fun showGizmo(
        viewMatrixBuffer: FloatBuffer,
        projectionMatrixBuffer: FloatBuffer,
        panelLayout: PanelLayout,
        transform: Transform,
        aabb: AABB,
    ): Boolean {
        viewMatrixBuffer.get(viewMatrix)
        projectionMatrixBuffer.get(projectionMatrix)
        aabb.worldAABB.apply {
            min.apply {
                bounds[0] = x
                bounds[1] = y
                bounds[2] = z
            }
            max.apply {
                bounds[3] = x
                bounds[4] = y
                bounds[5] = z
            }
        }

        keyBindingsInfo()

        debugPanel()

        return editTransform(panelLayout, transform)
    }

    private fun editTransform(panelLayout: PanelLayout, transform: Transform): Boolean = panelLayout.run {
        transform.get(objectMatrix)
        transform.get(objectMatrixBefore)

        if (ImGuizmo.isUsing()) {
            ImGuizmo.decomposeMatrixToComponents(
                objectMatrix,
                translation,
                rotation,
                scale
            )
        }

        ImGui.setNextWindowPos(midPanelPositionX, panelPositionY)
        ImGui.setNextWindowSize(midPanelWidth, midPanelHeight)
        val windowFlags = 0 or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoTitleBar
        de.hanno.hpengine.graphics.imgui.dsl.ImGui.window("Gizmo", windowFlags) {
            input.swallowInput = ImGui.isItemHovered()
            ImGui.beginChild("prevent_window_from_moving_by_drag", 0f, 0f, false, ImGuiWindowFlags.NoMove)

            ImGuizmo.setOrthographic(false)
            ImGuizmo.setEnabled(true)
            ImGuizmo.setDrawList()

            ImGuizmo.setRect(windowPositionX, windowPositionY, windowWidth, windowHeight)

//    ImGuizmo.drawGrid(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, IDENTITY_MATRIX, 100)
            ImGuizmo.setId(0)
//    ImGuizmo.drawCubes(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX)

            when {
                useSnap.get() && showBoundingSize.get() && boundSizingSnap -> {
                    ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue, snapValue, bounds, boundsSnap)
                }
                useSnap.get() && showBoundingSize.get() -> {
                    ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue, snapValue, bounds)
                }
                showBoundingSize.get() && boundSizingSnap -> {
                    ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue, empty, bounds, boundsSnap)
                }
                showBoundingSize.get() -> {
                    ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue, empty, bounds)
                }
                useSnap.get() -> {
                    ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue, snapValue)
                }
                else -> ImGuizmo.manipulate(viewMatrix, projectionMatrix, objectMatrix, currentGizmoOperation, currentSpace.imGuiValue)
            }

            val viewManipulateRight = midPanelPositionX + midPanelHeight
            val viewManipulateTop = panelPositionY
            ImGuizmo.viewManipulate(viewMatrix, camDistance.toFloat(), floatArrayOf(viewManipulateRight - 128, viewManipulateTop), viewManipulationSize, 0x10101010)

            ImGui.endChild()
        }

        transform.set(objectMatrix)
//    viewMatrix.set(INPUT_CAMERA_VIEW).invert() TODO: This flickers because of multithreading
        return !objectMatrixBefore.contentEquals(objectMatrix)
    }

    fun renderTransformationConfig() {
        ImGui.inputFloat3("Tr", translation, "%.3f", ImGuiInputTextFlags.ReadOnly)
        ImGui.inputFloat3("Rt", rotation, "%.3f", ImGuiInputTextFlags.ReadOnly)
        ImGui.inputFloat3("Sc", scale, "%.3f", ImGuiInputTextFlags.ReadOnly)

        if (ImGuizmo.isUsing()) {
            ImGuizmo.recomposeMatrixFromComponents(
                objectMatrix,
                translation,
                rotation,
                scale
            )
        }

        if (currentGizmoOperation != Operation.SCALE) {
            if (ImGui.radioButton("Local", currentSpace == Space.Local)) {
                currentSpace = Space.Local
            }
            ImGui.sameLine()
            if (ImGui.radioButton("World", currentSpace == Space.World)) {
                currentSpace = Space.World
            }
        }

        if (ImGui.radioButton("Translate", currentGizmoOperation == Operation.TRANSLATE)) {
            currentGizmoOperation = Operation.TRANSLATE
        }
        ImGui.sameLine()
        if (ImGui.radioButton("Rotate", currentGizmoOperation == Operation.ROTATE)) {
            currentGizmoOperation = Operation.ROTATE
        }
        ImGui.sameLine()
        if (ImGui.radioButton("Scale", currentGizmoOperation == Operation.SCALE)) {
            currentGizmoOperation = Operation.SCALE
        }

        ImGui.checkbox("Snap Checkbox", useSnap)

        tempFloat.set(snapValue[0])
        when (currentGizmoOperation) {
            Operation.TRANSLATE -> ImGui.inputFloat3("Snap Value", snapValue)
            Operation.ROTATE -> {
                ImGui.inputFloat("Angle Value", tempFloat)
                val rotateValue = tempFloat.get()
                Arrays.fill(snapValue, rotateValue) //avoiding allocation
            }

            Operation.SCALE -> {
                ImGui.inputFloat("Scale Value", tempFloat)
                val scaleValue = tempFloat.get()
                Arrays.fill(snapValue, scaleValue)
            }
        }

        ImGui.checkbox("Show Bound Sizing", showBoundingSize)

        if (showBoundingSize.get()) {
            if (ImGui.checkbox("BoundSizingSnap", boundSizingSnap)) {
                boundSizingSnap = !boundSizingSnap
            }
            ImGui.sameLine()
            ImGui.inputFloat3("Snap", boundsSnap)
        }
    }
}

private fun keyBindingsInfo() {
    if (showDebugPanel) {
        ImGui.text("Keybindings:")
        ImGui.text("T - Translate")
        ImGui.text("R - Rotate")
        ImGui.text("S - Scale")
        ImGui.separator()
    }
}

private fun debugPanel() {
    if (showDebugPanel) {
        if (ImGuizmo.isUsing()) {
            ImGui.text("Using gizmo")
            if (ImGuizmo.isOver()) {
                ImGui.text("Over a gizmo")
            }
            if (ImGuizmo.isOver(Operation.TRANSLATE)) {
                ImGui.text("Over translate gizmo")
            } else if (ImGuizmo.isOver(Operation.ROTATE)) {
                ImGui.text("Over rotate gizmo")
            } else if (ImGuizmo.isOver(Operation.SCALE)) {
                ImGui.text("Over scale gizmo")
            }
        } else {
            ImGui.text("Not using gizmo")
        }
    }
}