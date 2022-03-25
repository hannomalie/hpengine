package de.hanno.hpengine.engine.graphics.imgui

import de.hanno.hpengine.engine.transform.Transform
import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Mode
import imgui.extension.imguizmo.flag.Operation
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW.*
import java.nio.FloatBuffer
import java.util.*


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
private var currentGizmoOperation = 0

private var boundSizingSnap = false

fun showGizmo(
    viewMatrix: Matrix4f,
    viewMatrixAsBuffer: FloatBuffer,
    projectionMatrixAsBuffer: FloatBuffer,
    fovY: Float,
    near: Float,
    far: Float,
    editorCameraInputSystem: EditorCameraInputSystem,
    windowWidth: Float,
    windowHeight: Float,
    panelWidth: Float,
    panelHeight: Float,
    windowPositionX: Float,
    windowPositionY: Float,
    panelPositionX: Float,
    panelPositionY: Float,
    transform: Transform?,
) {
    viewMatrixAsBuffer.get(INPUT_CAMERA_VIEW)
//    Util.createPerspective(fovY, windowWidth / windowHeight, near, far).get(INPUT_CAMERA_PROJECTION)
    projectionMatrixAsBuffer.get(INPUT_CAMERA_PROJECTION)

    ImGui.text("Keybindings:")
    ImGui.text("T - Translate")
    ImGui.text("R - Rotate")
    ImGui.text("S - Scale")
    ImGui.separator()

    if (ImGuizmo.isUsing()) {
        editorCameraInputSystem.editorCameraInputComponent.cameraControlsEnabled = false
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
        editorCameraInputSystem.editorCameraInputComponent.cameraControlsEnabled = true
        ImGui.text("Not using gizmo")
    }

    transform?.let {
        editTransform(
            windowWidth,
            windowHeight,
            windowPositionX,
            windowPositionY,
            panelWidth,
            panelHeight,
            panelPositionX,
            panelPositionY,
            it,
            viewMatrix,
        )
    }
}

fun editTransform(
    windowWidth: Float,
    windowHeight: Float,
    windowPositionX: Float,
    windowPositionY: Float,
    panelWidth: Float,
    panelHeight: Float,
    panelPositionX: Float,
    panelPositionY: Float,
    transform: Transform,
    viewMatrix: Matrix4f,
) {

    transform.get(OBJECT_MATRIX)

    if (ImGui.isKeyPressed(GLFW_KEY_T)) {
        currentGizmoOperation = Operation.TRANSLATE
    } else if (ImGui.isKeyPressed(GLFW_KEY_R)) {
        currentGizmoOperation = Operation.ROTATE
    } else if (ImGui.isKeyPressed(GLFW_KEY_S)) {
        currentGizmoOperation = Operation.SCALE
    } else if (ImGui.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
        USE_SNAP.set(!USE_SNAP.get())
    }

    if (ImGuizmo.isUsing()) {
        ImGuizmo.decomposeMatrixToComponents(
            OBJECT_MATRIX,
            INPUT_MATRIX_TRANSLATION,
            INPUT_MATRIX_ROTATION,
            INPUT_MATRIX_SCALE
        )
    }

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

    ImGui.setNextWindowPos(panelPositionX, panelPositionY)
    ImGui.setNextWindowSize(panelWidth, panelHeight)
    val windowFlags = 0 or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoTitleBar
    ImGui.begin("Gizmo", windowFlags)
    ImGui.beginChild("prevent_window_from_moving_by_drag", 0f, 0f, false, ImGuiWindowFlags.NoMove)

    ImGuizmo.setOrthographic(false)
    ImGuizmo.setEnabled(true)
    ImGuizmo.setDrawList()

    ImGuizmo.setRect(windowPositionX, windowPositionY, windowWidth, windowHeight)

//    ImGuizmo.drawGrid(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, IDENTITY_MATRIX, 100)
    ImGuizmo.setId(0)
//    ImGuizmo.drawCubes(INPUT_CAMERA_VIEW, INPUT_CAMERA_PROJECTION, OBJECT_MATRIX)

    if (USE_SNAP.get() && BOUNDING_SIZE.get() && boundSizingSnap) {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode,
            INPUT_SNAP_VALUE,
            INPUT_BOUNDS,
            INPUT_BOUNDS_SNAP
        )
    } else if (USE_SNAP.get() && BOUNDING_SIZE.get()) {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode,
            INPUT_SNAP_VALUE,
            INPUT_BOUNDS
        )
    } else if (BOUNDING_SIZE.get() && boundSizingSnap) {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode,
            EMPTY,
            INPUT_BOUNDS,
            INPUT_BOUNDS_SNAP
        )
    } else if (BOUNDING_SIZE.get()) {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode,
            EMPTY,
            INPUT_BOUNDS
        )
    } else if (USE_SNAP.get()) {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode,
            INPUT_SNAP_VALUE
        )
    } else {
        ImGuizmo.manipulate(
            INPUT_CAMERA_VIEW,
            INPUT_CAMERA_PROJECTION,
            OBJECT_MATRIX,
            currentGizmoOperation,
            currentMode
        )
    }

    val viewManipulateRight = panelPositionX + panelHeight
    val viewManipulateTop = panelPositionY
    ImGuizmo.viewManipulate(
        INPUT_CAMERA_VIEW,
        CAM_DISTANCE.toFloat(),
        floatArrayOf(viewManipulateRight - 128, viewManipulateTop),
        VIEW_MANIPULATE_SIZE,
        0x10101010
    )

    ImGui.endChild()
    ImGui.end()

    transform.set(OBJECT_MATRIX)
//    viewMatrix.set(INPUT_CAMERA_VIEW).invert() TODO: This flickers because of multithreading
}
