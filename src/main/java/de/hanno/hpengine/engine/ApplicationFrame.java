package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.renderer.*;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;

import javax.swing.*;
import javax.swing.Renderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import static de.hanno.hpengine.renderer.Renderer.*;

public class ApplicationFrame extends JFrame {

    public static int WINDOW_WIDTH = Config.WIDTH;
    public static int WINDOW_HEIGHT = Config.HEIGHT;
    Canvas canvas;

    public ApplicationFrame() throws HeadlessException {
        super("hpengine");
        setVisible(false);
        setSize(new Dimension(Config.WIDTH, Config.HEIGHT));
        JLayeredPane layeredPane = new JLayeredPane();
        canvas = new Canvas() {
            @Override
            public void addNotify() {
                super.addNotify();
            }

            @Override
            public void removeNotify() {
                super.removeNotify();
            }
        };
        canvas.setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
        canvas.setIgnoreRepaint(true);
        layeredPane.add(canvas, 0);
//        JPanel overlayPanel = new JPanel();
//        overlayPanel.setOpaque(true);
//        overlayPanel.add(new JButton("asdasdasd"));
//        layeredPane.add(overlayPanel, 1);
        add(layeredPane);
        setLayout(new BorderLayout());
//        getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
//        getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
        getContentPane().add(canvas, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        try {
            Display.setParent(canvas);
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
        ApplicationFrame frame = this;
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
                WINDOW_WIDTH = frame.getWidth();
                WINDOW_HEIGHT = frame.getHeight();
            }
        });

        new TimeStepThread("DisplayTitleUpdate", 1.0f) {

            @Override
            public void update(float seconds) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        frame.setTitle(String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
                                getInstance().getCurrentFPS(), getInstance().getMsPerFrame(), Engine.getInstance().getFPSCounter().getFPS(), Engine.getInstance().getFPSCounter().getMsPerFrame()));
                    } catch (ArrayIndexOutOfBoundsException e) { /*yea, i know...*/}
                    catch (IllegalStateException | NullPointerException e) {
                        frame.setTitle("HPEngine Renderer initializing...");
                    }

                });
            }
        }.start();
    }
}
