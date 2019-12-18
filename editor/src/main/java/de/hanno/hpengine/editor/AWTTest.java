package de.hanno.hpengine.editor;

import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame;
import org.pushingpixels.substance.api.SubstanceCortex;
import org.pushingpixels.substance.api.skin.MarinerSkin;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;

public class AWTTest {
    public static void main(String[] args) throws InvocationTargetException, InterruptedException {
        final RibbonEditor[] frame = new RibbonEditor[1];
        SwingUtils.INSTANCE.invokeAndWait(() -> {
            JRibbonFrame.setDefaultLookAndFeelDecorated(true);
            SubstanceCortex.GlobalScope.setSkin(new MarinerSkin());
            frame[0] = new RibbonEditor();
            frame[0].setPreferredSize(new Dimension(600,600));
            return null;
        });
        frame[0].setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        GLData data = new GLData();
        data.samples = 4;
        data.swapInterval = 0;
        AWTGLCanvas canvas = new AWTGLCanvas(data) {
            private static final long serialVersionUID = 1L;
            int w = getWidth();
            int h = getHeight();

            public void initGL() {
                System.out.println("OpenGL version: " + effective.majorVersion + "." + effective.minorVersion + " (Profile: " + effective.profile + ")");
                createCapabilities();
                glClearColor(0.3f, 0.4f, 0.5f, 1);
            }
            public void paintGL() {
                w = getWidth();
                h = getHeight();
                float aspect = (float) w / h;
                glClear(GL_COLOR_BUFFER_BIT);
                glViewport(0, 0, w, h);
                glBegin(GL_QUADS);
                glColor3f(0.4f, 0.6f, 0.8f);
                glVertex2f(-0.75f / aspect, 0.0f);
                glVertex2f(0, -0.75f);
                glVertex2f(+0.75f / aspect, 0);
                glVertex2f(0, +0.75f);
                glEnd();
                swapBuffers();
            }
        };
        canvas.setFocusable(true);
        frame[0].add(canvas, BorderLayout.CENTER);
//        frame[0].getMainPanel().add(canvas, BorderLayout.CENTER);
        SwingUtilities.invokeAndWait(() -> {
            frame[0].pack();
        });
        frame[0].setVisible(true);
        frame[0].transferFocus();

        Runnable renderLoop = new Runnable() {
			public void run() {
				if (!canvas.isValid())
					return;
				canvas.render();
                SwingUtilities.invokeLater(this);
			}
		};
        SwingUtilities.invokeLater(renderLoop);
    }
}
