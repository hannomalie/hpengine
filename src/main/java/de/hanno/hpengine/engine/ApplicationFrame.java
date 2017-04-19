package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.renderer.GraphicsContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import static de.hanno.hpengine.renderer.Renderer.getInstance;

public class ApplicationFrame extends JFrame {

    private CanvasWrapper canvasWrapper;

    private Runnable setTitleRunnable = () -> {
        try {
            ApplicationFrame.this.setTitle(String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
                    getInstance().getCurrentFPS(), getInstance().getMsPerFrame(), Engine.getInstance().getFPSCounter().getFPS(), Engine.getInstance().getFPSCounter().getMsPerFrame()));
        } catch (ArrayIndexOutOfBoundsException e) { /*yea, i know...*/} catch (IllegalStateException | NullPointerException e) {
            ApplicationFrame.this.setTitle("HPEngine Renderer initializing...");
        }
    };

    public ApplicationFrame() throws HeadlessException {
        super("hpengine");
        setVisible(false);
        setSize(new Dimension(Config.getInstance().getWidth(), Config.getInstance().getHeight()));
        JLayeredPane layeredPane = new JLayeredPane();
        Canvas canvas = new Canvas() {
            @Override
            public void addNotify() {
                super.addNotify();
            }

            @Override
            public void removeNotify() {
                super.removeNotify();
            }
        };
        canvas.setPreferredSize(new Dimension(Config.getInstance().getWidth(), Config.getInstance().getHeight()));
        canvas.setIgnoreRepaint(true);
        canvasWrapper = new CanvasWrapper(canvas, setTitleRunnable);
        layeredPane.add(canvasWrapper.getCanvas(), 0);
//        JPanel overlayPanel = new JPanel();
//        overlayPanel.setOpaque(true);
//        overlayPanel.add(new JButton("asdasdasd"));
//        layeredPane.add(overlayPanel, 1);
        add(layeredPane);
        setLayout(new BorderLayout());
//        getContentPane().add(new JButton("adasd"), BorderLayout.PAGE_START);
//        getContentPane().add(new JButton("xxx"), BorderLayout.PAGE_END);
        getContentPane().add(canvasWrapper.getCanvas(), BorderLayout.CENTER);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);
        addComponentListener(new ComponentAdapter() {
            int retryCounter = 0;
            public void componentResized(ComponentEvent evt) {
                try {
                    GraphicsContext.getInstance().setCanvasWidth(ApplicationFrame.this.getWidth());
                    GraphicsContext.getInstance().setCanvasHeight(ApplicationFrame.this.getHeight());
                    retryCounter = 0;
                } catch (IllegalStateException e) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    if(retryCounter < 5) {
                        componentResized(evt);
                        retryCounter++;
                    }
                }

            }
        });
    }

    public CanvasWrapper getRenderCanvas() {
        return canvasWrapper;
    }

    public void attachGame() {
        GraphicsContext.getInstance().execute(() -> {
            GraphicsContext.getInstance().attach(canvasWrapper);
            Engine.getInstance().setSetTitleRunnable(ApplicationFrame.this.getSetTitleRunnable());
            setVisible(true);
        });
    }

    public Runnable getSetTitleRunnable() {
        return setTitleRunnable;
    }
}
