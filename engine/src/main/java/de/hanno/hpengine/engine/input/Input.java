package de.hanno.hpengine.engine.input;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.event.ClickEvent;
import de.hanno.hpengine.engine.graphics.renderer.GpuContext;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private IntArrayList currentKeys = new IntArrayList();
    private IntArrayList keysPressed = new IntArrayList();
    private IntArrayList keysReleased = new IntArrayList();

    private IntArrayList currentMouse = new IntArrayList();
    private IntArrayList downMouse = new IntArrayList();
    private IntArrayList upMouse = new IntArrayList();

    private final int FIRST_KEY = GLFW_KEY_SPACE;
    private final int NUM_KEYS = GLFW_KEY_LAST - GLFW_KEY_SPACE;
    private final int NUM_BUTTONS = 3;

    private int dx;
    private int dy;

    private int dxLast;
    private int dyLast;

    private int dxBeforeLast;
    private int dyBeforeLast;

    private final boolean HOLD_KEYS_AS_PRESSED_KEYS = true;

    private boolean MOUSE_LEFT_PRESSED_LAST_FRAME;
    private boolean STRG_PRESSED_LAST_FRAME = false;
    public volatile int PICKING_CLICK = 0;
    private double[] mouseX = new double[1];
    private double[] mouseY = new double[1];
    private double[] mouseXLast = new double[1];
    private double[] mouseYLast = new double[1];
    private final GpuContext gpuContext;

    public Input(GpuContext gpuContext) {
        this.gpuContext = gpuContext;
    }

    public void update() {
        updateKeyboard();
        updateMouse();
    }

    private void updateKeyboard() {

        if(isMouseClicked(0)) {
            if(!MOUSE_LEFT_PRESSED_LAST_FRAME) {
                Engine.getEventBus().post(new ClickEvent());
            }
            MOUSE_LEFT_PRESSED_LAST_FRAME = true;
        } else {
            MOUSE_LEFT_PRESSED_LAST_FRAME = false;
        }
        {
            if (PICKING_CLICK == 0 && isKeyDown(gpuContext, GLFW_KEY_LEFT_CONTROL)) {
                if (isMouseClicked(0) && !STRG_PRESSED_LAST_FRAME) {
                    PICKING_CLICK = 1;
                    STRG_PRESSED_LAST_FRAME = true;
                }
            } else {
                STRG_PRESSED_LAST_FRAME = false;
            }
        }

        keysPressed.clear();
        keysReleased.clear();

        if(HOLD_KEYS_AS_PRESSED_KEYS) {
            currentKeys.clear();
        }

        for (int i = FIRST_KEY; i < NUM_KEYS; i++) {
            if (isKeyDown(gpuContext, i) && !currentKeys.contains(i)) {
                keysPressed.add(i);
            }
        }

        for (int i = FIRST_KEY; i < NUM_KEYS; i++) {
            if (!isKeyDown(gpuContext, i) && currentKeys.contains(i)) {
                keysReleased.add(i);
            }
        }

        currentKeys.clear();

        for (int i = 0; i < NUM_KEYS; i++) {
            if (isKeyDown(gpuContext, FIRST_KEY + i)) {
                currentKeys.add(i);
            }
        }
    }

    private void updateMouse() {
        downMouse.clear();
        upMouse.clear();

        if(HOLD_KEYS_AS_PRESSED_KEYS) {
            currentMouse.clear();
        }

        for (int i = 0; i < NUM_BUTTONS; i++) {
            if (isMouseDown(i) && !currentMouse.contains(i)) {
                downMouse.add(i);
            }
        }

        for (int i = 0; i < NUM_BUTTONS; i++) {
            if (!isMouseDown(i) && currentMouse.contains(i)) {
                upMouse.add(i);
            }
        }

        currentMouse.clear();

        for (int i = 0; i < NUM_BUTTONS; i++) {
            if (isMouseDown(i)) {
                currentMouse.add(i);
            }
        }

        dxBeforeLast = dxLast;
        dyBeforeLast = dyLast;
        dxLast = dx;
        dyLast = dy;
        mouseXLast[0] = mouseX[0];
        mouseYLast[0] = mouseY[0];
        glfwGetCursorPos(gpuContext.getWindowHandle(), mouseX, mouseY);
        dx = (int) -(mouseXLast[0] - mouseX[0]);
        dy = (int) (mouseYLast[0] - mouseY[0]);
    }


    private boolean isKeyDown(GpuContext gpuContext, int keyCode) {
        return glfwGetKey(gpuContext.getWindowHandle(), keyCode) == GLFW_PRESS;
    }

    public boolean isKeyPressed(int keyCode) {
        return keysPressed.contains(keyCode);
    }

    public boolean isKeyUp(int keyCode) {
        return keysReleased.contains(keyCode);
    }


    private boolean isMouseDown(int buttonCode) {
        return glfwGetMouseButton(gpuContext.getWindowHandle(), buttonCode) == GLFW_PRESS;
    }

    public boolean isMouseClicked(int buttonCode) {
        return downMouse.contains(buttonCode);
    }

    public boolean isMouseUp(int buttonCode) {
        return upMouse.contains(buttonCode);
    }

    public int getDX() {
        return dx;
    }

    public int getDY() {
        return dy;
    }

    public int getDXSmooth() {
        return (dx + dxLast + dxBeforeLast) / 3;
    }

    public int getDYSmooth() {
        return (dy + dyLast + dyBeforeLast) / 3;
    }

    public int getMouseX() {
        return (int) mouseX[0];
    }

    public int getMouseY() {
        return Config.getInstance().getHeight() - (int) mouseY[0];
    }
}
