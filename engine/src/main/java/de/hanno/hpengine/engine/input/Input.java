package de.hanno.hpengine.engine.input;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.event.ClickEvent;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private static IntArrayList currentKeys = new IntArrayList();
    private static IntArrayList keysPressed = new IntArrayList();
    private static IntArrayList keysReleased = new IntArrayList();

    private static IntArrayList currentMouse = new IntArrayList();
    private static IntArrayList downMouse = new IntArrayList();
    private static IntArrayList upMouse = new IntArrayList();

    private static final int FIRST_KEY = GLFW_KEY_SPACE;
    private static final int NUM_KEYS = GLFW_KEY_LAST - GLFW_KEY_SPACE;
    private static final int NUM_BUTTONS = 3;

    private static int dx;
    private static int dy;

    private static int dxLast;
    private static int dyLast;

    private static int dxBeforeLast;
    private static int dyBeforeLast;

    private static final boolean HOLD_KEYS_AS_PRESSED_KEYS = true;

    private static boolean MOUSE_LEFT_PRESSED_LAST_FRAME;
    private static boolean STRG_PRESSED_LAST_FRAME = false;
    public static volatile int PICKING_CLICK = 0;
    private static double[] mouseX = new double[1];
    private static double[] mouseY = new double[1];
    private static double[] mouseXLast = new double[1];
    private static double[] mouseYLast = new double[1];

    public static void update() {
        updateKeyboard();
        updateMouse();
    }

    private static void updateKeyboard() {

        if(Input.isMouseClicked(0)) {
            if(!MOUSE_LEFT_PRESSED_LAST_FRAME) {
                Engine.getEventBus().post(new ClickEvent());
            }
            MOUSE_LEFT_PRESSED_LAST_FRAME = true;
        } else {
            MOUSE_LEFT_PRESSED_LAST_FRAME = false;
        }
        {
            if (PICKING_CLICK == 0 && Input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
                if (Input.isMouseClicked(0) && !STRG_PRESSED_LAST_FRAME) {
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
            if (isKeyDown(i) && !currentKeys.contains(i)) {
                keysPressed.add(i);
            }
        }

        for (int i = FIRST_KEY; i < NUM_KEYS; i++) {
            if (!isKeyDown(i) && currentKeys.contains(i)) {
                keysReleased.add(i);
            }
        }

        currentKeys.clear();

        for (int i = 0; i < NUM_KEYS; i++) {
            if (isKeyDown(FIRST_KEY + i)) {
                currentKeys.add(i);
            }
        }
    }

    private static void updateMouse() {
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
        glfwGetCursorPos(GraphicsContext.getInstance().getWindowHandle(), mouseX, mouseY);
        dx = (int) -(mouseXLast[0] - mouseX[0]);
        dy = (int) (mouseYLast[0] - mouseY[0]);
    }


    private static boolean isKeyDown(int keyCode) {
        return glfwGetKey(GraphicsContext.getInstance().getWindowHandle(), keyCode) == GLFW_PRESS;
    }

    public static boolean isKeyPressed(int keyCode) {
        return keysPressed.contains(keyCode);
    }

    public static boolean isKeyUp(int keyCode) {
        return keysReleased.contains(keyCode);
    }


    private static boolean isMouseDown(int buttonCode) {
        return glfwGetMouseButton(GraphicsContext.getInstance().getWindowHandle(), buttonCode) == GLFW_PRESS;
    }

    public static boolean isMouseClicked(int buttonCode) {
        return downMouse.contains(buttonCode);
    }

    public static boolean isMouseUp(int buttonCode) {
        return upMouse.contains(buttonCode);
    }

    public static int getDX() {
        return dx;
    }

    public static int getDY() {
        return dy;
    }

    public static int getDXSmooth() {
        return (dx + dxLast + dxBeforeLast) / 3;
    }

    public static int getDYSmooth() {
        return (dy + dyLast + dyBeforeLast) / 3;
    }

    public static int getMouseX() {
        return (int) mouseX[0];
    }

    public static int getMouseY() {
        return Config.getInstance().getHeight() - (int) mouseY[0];
    }
}
