package test;

import config.Config;
import event.bus.MBassadorEventBus;
import org.junit.BeforeClass;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import renderer.OpenGLContext;

import javax.swing.*;
import java.awt.*;

public class TestWithOpenGLContext {

    @BeforeClass
    public static void init() throws LWJGLException {
        Config.setHeadless(true);
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
        Display.setParent(canvas);
        OpenGLContext.getInstance();
    }
}
