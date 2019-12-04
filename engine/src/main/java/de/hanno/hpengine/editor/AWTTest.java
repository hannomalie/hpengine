package de.hanno.hpengine.editor;

import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;

public class AWTTest {
    public static void main(String[] args) {
        JFrame frame = new JFrame("AWT test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setPreferredSize(new Dimension(600, 600));
        GLData data = new GLData();
        data.samples = 4;
        data.swapInterval = 0;
        AWTGLCanvas canvas;
        frame.add(canvas = new AWTGLCanvas(data) {
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
        }, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
        frame.transferFocus();

        ExecutorService pool = Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("XXXXXXXXXXXXXXXXX");
            thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
            return thread;
        });
        Runnable renderLoop = new Runnable() {
			public void run() {
				if (!canvas.isValid())
					return;
				canvas.render();
                pool.submit(this);
//                SwingUtilities.invokeLater(this);
			}
		};
		pool.submit(renderLoop);
//        SwingUtilities.invokeLater(renderLoop);
    }
}
