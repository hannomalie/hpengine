package de.hanno.hpengine;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.lwjgl.LWJGLException;

import javax.swing.*;
import java.awt.*;

public class TestWithOpenGLContext {

    @BeforeClass
    public static void init() throws LWJGLException {
        Canvas canvas = new Canvas();
        JFrame frame = new JFrame("hpengine");
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.add(canvas, 0);
        frame.getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
        frame.getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
        frame.getContentPane().add(canvas, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(false);
        GraphicsContext.initGpuContext();
        Engine.getInstance().getGpuContext();
    }

    @AfterClass
    public static void destroy() {
        Engine.getInstance().getGpuContext().destroy();
    }
}
