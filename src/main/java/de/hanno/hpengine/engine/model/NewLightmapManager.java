package de.hanno.hpengine.engine.model;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.event.SceneInitEvent;
import de.hanno.hpengine.event.bus.EventBus;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.drawstrategy.extensions.DrawLightMapExtension;
import net.engio.mbassy.listener.Handler;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NewLightmapManager {
    private static final NewLightmapManager instance = new NewLightmapManager();
    static {
        EventBus.getInstance().register(instance);
    }
    private List<WeakReference<Model>> models = new ArrayList<>();
    public static int MAX_WIDTH = 0;
    public static int MAX_HEIGHT = 0;

    public static NewLightmapManager getInstance() {
        return instance;
    }

    public void registerForLightmapCoordsUpdate(Model model) {
        this.models.add(new WeakReference(model));
        updateLightmapCoords();
    }

    public void updateLightmapCoords() {
        List<Model.CompiledFace> faces = new ArrayList<>(1000);
        for(WeakReference<Model> currentModelRef : models) {
            Model currentModel = currentModelRef.get();
            if(currentModel != null) {
                faces.addAll(currentModel.getFaces());
            } else {
                models.remove(currentModelRef);
            }
        }

        List<Model.CompiledFace> sortedByLength = faces.stream().sorted((Model.CompiledFace faceOne, Model.CompiledFace faceTwo) -> {
            Vector2f aOne = new Vector2f(faceOne.vertices[0].initialLightmapCoords.x, faceOne.vertices[0].initialLightmapCoords.y);
            Vector2f cOne = new Vector2f(faceOne.vertices[2].initialLightmapCoords.x, faceOne.vertices[2].initialLightmapCoords.y);
            float heightOne = Vector2f.sub(aOne, cOne, null).length();
            Vector2f aTwo = new Vector2f(faceTwo.vertices[0].initialLightmapCoords.x, faceTwo.vertices[0].initialLightmapCoords.y);
            Vector2f cTwo = new Vector2f(faceTwo.vertices[2].initialLightmapCoords.x, faceTwo.vertices[2].initialLightmapCoords.y);
            float heightTwo = Vector2f.sub(aTwo, cTwo, null).length();
            return Float.compare(heightTwo, heightOne);
        }).collect(Collectors.toList());

        if(sortedByLength.isEmpty()) {
            return;
        }

        MAX_WIDTH = DrawLightMapExtension.WIDTH;
        MAX_HEIGHT = 1;
        int currentWidth = 0;
        int currentHeight = 0;
        List<Vector3f> copy = new ArrayList();
        float lastRowsMaxHeight = 0;

        for(int i = 0; i < sortedByLength.size(); i++) {
            Model.CompiledFace currentFace = sortedByLength.get(i);
            Vector2f a = new Vector2f(currentFace.vertices[0].initialLightmapCoords.x, currentFace.vertices[0].initialLightmapCoords.y);
            Vector2f b = new Vector2f(currentFace.vertices[1].initialLightmapCoords.x, currentFace.vertices[1].initialLightmapCoords.y);
            Vector2f c = new Vector2f(currentFace.vertices[2].initialLightmapCoords.x, currentFace.vertices[2].initialLightmapCoords.y);
            Vector2f widthVector = Vector2f.sub(a, b, null);
            Vector2f heightVector = Vector2f.sub(a, c, null);
            float width = widthVector.length();
            float height = heightVector.length();
            if(width < 1.0f) {
                Vector2f.add(a, widthVector.normalise(null), b);
            } else if(width > 4f) {
                Vector2f.add(a, (Vector2f) widthVector.normalise(null).scale(10.0f), b);
            }
            if(height < 1.0f) {
                Vector2f.add(a, heightVector.normalise(null), c);
            } else if(width > 4f) {
                Vector2f.add(a, (Vector2f) heightVector.normalise(null).scale(10.0f), c);
            }


            if(currentWidth + width < MAX_WIDTH) {
                lastRowsMaxHeight = Math.max(lastRowsMaxHeight, height);
            } else {
                currentWidth = 0;
                currentHeight = currentHeight + (int) (lastRowsMaxHeight + 1);
                lastRowsMaxHeight = height;
            }
            setTheNewValues(currentWidth, currentHeight, copy, currentFace, width, height);
            currentWidth += width + 1;
            MAX_HEIGHT = Math.max(MAX_HEIGHT, (int) (currentHeight+height+1));
        }

        if(MAX_HEIGHT > DrawLightMapExtension.HEIGHT && MAX_HEIGHT <= 1024) {
            GraphicsContext.getInstance().execute(() -> {
                DrawLightMapExtension.WIDTH *= 2;
                DrawLightMapExtension.HEIGHT = DrawLightMapExtension.WIDTH;
               DrawLightMapExtension.staticLightmapTarget.resize(DrawLightMapExtension.WIDTH, DrawLightMapExtension.HEIGHT);
            }, true);
            updateLightmapCoords();
        }
        frame.init(copy).show();
    }

    public void setTheNewValues(int currentWidth, int currentHeight, List<Vector3f> copy, Model.CompiledFace currentFace, float width, float height) {
        currentFace.vertices[0].lightmapCoords.x = currentWidth;
        currentFace.vertices[0].lightmapCoords.y = currentHeight;
        currentFace.vertices[1].lightmapCoords.x = currentWidth+width;
        currentFace.vertices[1].lightmapCoords.y = currentHeight;
        currentFace.vertices[2].lightmapCoords.x = currentWidth;
        currentFace.vertices[2].lightmapCoords.y = currentHeight+height;
        copy.add(currentFace.vertices[0].lightmapCoords);
        copy.add(currentFace.vertices[1].lightmapCoords);
        copy.add(currentFace.vertices[2].lightmapCoords);
    }

    private static LightmapFrame frame = new LightmapFrame();

    public void reset() {
        models.clear();
    }

    private static class LightmapFrame extends JFrame {

        private List<Vector3f> copy = new ArrayList();

        public LightmapFrame(){
            this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {

                    super.paintComponent(g);
                    for(int i = 0; i < copy.size(); i+=3) {
                        int[] xPoints = new int[3];
                        int[] yPoints = new int[3];
                        xPoints[0] = (int) (copy.get(i).getX() * this.getWidth() / MAX_WIDTH);
                        xPoints[1] = (int) (copy.get(i+1).getX() * this.getWidth() / MAX_WIDTH);
                        xPoints[2] = (int) (copy.get(i+2).getX() * this.getWidth() / MAX_WIDTH);

                        yPoints[0] = (int) (copy.get(i).getY() * this.getHeight() / MAX_HEIGHT);
                        yPoints[1] = (int) (copy.get(i+1).getY() * this.getHeight() / MAX_HEIGHT);
                        yPoints[2] = (int) (copy.get(i+2).getY() * this.getHeight() / MAX_HEIGHT);
                        g.setColor(Color.BLUE);
                        Polygon poly = new Polygon(xPoints, yPoints, xPoints.length);
                        g.drawPolygon(poly);
                    }
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(1280, 720);
                }
            };
            getContentPane().add(panel);
            Dimension size = new Dimension(1280, 720);
            setPreferredSize(size);
            setSize(size);
        }

        public LightmapFrame init(List<Vector3f> copy) {
            this.copy = copy;
            return this;
        }
    }


    @Subscribe
    @Handler
    public void handle(SceneInitEvent e) {
        NewLightmapManager.getInstance().reset();
    }

}
