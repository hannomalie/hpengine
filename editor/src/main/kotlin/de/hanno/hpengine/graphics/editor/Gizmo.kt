package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.Transform
import de.hanno.hpengine.graphics.editor.panels.PanelLayout
import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Mode
import imgui.extension.imguizmo.flag.Operation
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import org.lwjgl.glfw.GLFW.*
import java.nio.FloatBuffer
import java.util.*

// TODO: Change global variables for local ones, wrap in a class
private const val CAM_DISTANCE = 8
private const val FLT_EPSILON = 1.19209290E-07f

private val OBJECT_MATRIX = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f
)
private val IDENTITY_MATRIX = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f
)

private val VIEW_MANIPULATE_SIZE = floatArrayOf(128f, 128f)

private val EMPTY = floatArrayOf(0f)

private val INPUT_CAMERA_VIEW = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f
)
private var INPUT_CAMERA_PROJECTION = floatArrayOf(
    0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f,
    0f, 0f, 0f, 0f
)

private val INPUT_BOUNDS = floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
private val INPUT_BOUNDS_SNAP = floatArrayOf(1f, 1f, 1f)

private val INPUT_SNAP_VALUE = floatArrayOf(1f, 1f, 1f)
private val INPUT_MATRIX_TRANSLATION = FloatArray(3)
private val INPUT_MATRIX_SCALE = FloatArray(3)
private val INPUT_MATRIX_ROTATION = FloatArray(3)

private val INPUT_FLOAT = ImFloat()

private val BOUNDING_SIZE = ImBoolean(false)
private val USE_SNAP = ImBoolean(false)

private var currentMode = Mode.LOCAL
private var currentGizmoOperation = Operation.TRANSLATE

private var boundSizingSnap = false

private const val showDebugPanel: Boolean = false

fun ImGuiEditor.showGizmo(
    viewMatrixBuffer: FloatBuffer,
    projectionMatrixBuffer: FloatBuffer,
    editorCameraInputSystem: EditorCameraInputSystem,
    panelLayout: PanelLayout,
    transform: Transform,
) {
    viewMatrixBuffer.get(INPUT_CAMERA_VIEW)
    projectionMatrixBuffer.get(INPUT_CAMERA_PROJECTION)

    keyBindingsInfo()

//    editorCameraInputSystem.editorCameraInputComponent.prioritizeGameInput = !ImGuizmo.isUsing()

    debugPanel()

    editTransform(panelLayout, transform)
}

fun ImGuiEditor.editTransform(panelLayout: PanelLayout, transform: Transform): Unit = panelLayout.run {
    transform.get(OBJECT_MATRIX)

    if (ImGuizmo.isUsing()) {
        ImGuizmo.decomposeMatrixToComponents(
            OBJECT_MATRIX,
            INPUT_MATRIX_TRANSLATION,
            INPUT_MATRIX_ROTATION,
            INPUT_MATRIX_SCALE
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
            USE_SNAP.get() && BOUNDING_SIZE.get() && boundSizingSnap -> {
                ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode, INPUT_SNAP_VALUE, INPUT_BOUNDS, INPUT_BOUNDS_SNAP)
            }
            USE_SNAP.get() && BOUNDING_SIZE.get() -> {
                ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode, INPUT_SNAP_VALUE, INPUT_BOUNDS)
            }
            BOUNDING_SIZE.get() && boundSizingSnap -> {
                ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode, EMPTY, INPUT_BOUNDS, INPUT_BOUNDS_SNAP)
            }
            BOUNDING_SIZE.get() -> {
                ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode, EMPTY, INPUT_BOUNDS)
            }
            USE_SNAP.get() -> {
                ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode, INPUT_SNAP_VALUE)
            }
            else -> ImGuizmo.manipulate(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX, currentGizmoOperation, currentMode)
        }

        val viewManipulateRight = midPanelPositionX + midPanelHeight
        val viewManipulateTop = panelPositionY
        ImGuizmo.viewManipulate(INPUT_CAMERA_VIEW, CAM_DISTANCE.toFloat(), floatArrayOf(viewManipulateRight - 128, viewManipulateTop), VIEW_MANIPULATE_SIZE, 0x10101010)

        ImGui.endChild()
    }

    transform.set(OBJECT_MATRIX)
//    viewMatrix.set(INPUT_CAMERA_VIEW).invert() TODO: This flickers because of multithreading
}

fun renderTransformationConfig() {
    ImGui.inputFloat3("Tr", INPUT_MATRIX_TRANSLATION, "%.3f", ImGuiInputTextFlags.ReadOnly)
    ImGui.inputFloat3("Rt", INPUT_MATRIX_ROTATION, "%.3f", ImGuiInputTextFlags.ReadOnly)
    ImGui.inputFloat3("Sc", INPUT_MATRIX_SCALE, "%.3f", ImGuiInputTextFlags.ReadOnly)

    if (ImGuizmo.isUsing()) {
        ImGuizmo.recomposeMatrixFromComponents(
            OBJECT_MATRIX,
            INPUT_MATRIX_TRANSLATION,
            INPUT_MATRIX_ROTATION,
            INPUT_MATRIX_SCALE
        )
    }

    if (currentGizmoOperation != Operation.SCALE) {
        if (ImGui.radioButton("Local", currentMode == Mode.LOCAL)) {
            currentMode = Mode.LOCAL
        }
        ImGui.sameLine()
        if (ImGui.radioButton("World", currentMode == Mode.WORLD)) {
            currentMode = Mode.WORLD
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

    ImGui.checkbox("Snap Checkbox", USE_SNAP)

    INPUT_FLOAT.set(INPUT_SNAP_VALUE[0])
    when (currentGizmoOperation) {
        Operation.TRANSLATE -> ImGui.inputFloat3("Snap Value", INPUT_SNAP_VALUE)
        Operation.ROTATE -> {
            ImGui.inputFloat("Angle Value", INPUT_FLOAT)
            val rotateValue = INPUT_FLOAT.get()
            Arrays.fill(INPUT_SNAP_VALUE, rotateValue) //avoiding allocation
        }

        Operation.SCALE -> {
            ImGui.inputFloat("Scale Value", INPUT_FLOAT)
            val scaleValue = INPUT_FLOAT.get()
            Arrays.fill(INPUT_SNAP_VALUE, scaleValue)
        }
    }

    ImGui.checkbox("Show Bound Sizing", BOUNDING_SIZE)

    if (BOUNDING_SIZE.get()) {
        if (ImGui.checkbox("BoundSizingSnap", boundSizingSnap)) {
            boundSizingSnap = !boundSizingSnap
        }
        ImGui.sameLine()
        ImGui.inputFloat3("Snap", INPUT_BOUNDS_SNAP)
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