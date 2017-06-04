package de.hanno.hpengine.engine.model;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.event.SceneInitEvent;
import de.hanno.hpengine.engine.event.bus.EventBus;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLightMapExtension;
import net.engio.mbassy.listener.Handler;
import org.joml.Vector2f;
import org.joml.Vector3f;

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
    private List<WeakReference<Mesh>> models = new ArrayList<>();
    public static int MAX_WIDTH = 0;
    public static int MAX_HEIGHT = 0;

    public static NewLightmapManager getInstance() {
        return instance;
    }

    public void registerForLightmapCoordsUpdate(Mesh mesh) {
        this.models.add(new WeakReference(mesh));
        updateLightmapCoords();
    }

    public void updateLightmapCoords() {
        List<Mesh.CompiledFace> faces = new ArrayList<>(1000);
        for(WeakReference<Mesh> currentModelRef : models) {
            Mesh currentMesh = currentModelRef.get();
            if(currentMesh != null) {
                faces.addAll(currentMesh.getFaces());
            } else {
                models.remove(currentModelRef);
            }
        }

        List<Mesh.CompiledFace> sortedByLength = faces.stream().sorted((Mesh.CompiledFace faceOne, Mesh.CompiledFace faceTwo) -> {
            Vector2f aOne = new Vector2f(faceOne.vertices[0].initialLightmapCoords.x, faceOne.vertices[0].initialLightmapCoords.y);
            Vector2f cOne = new Vector2f(faceOne.vertices[2].initialLightmapCoords.x, faceOne.vertices[2].initialLightmapCoords.y);
            float heightOne = aOne.distance(cOne);
            Vector2f aTwo = new Vector2f(faceTwo.vertices[0].initialLightmapCoords.x, faceTwo.vertices[0].initialLightmapCoords.y);
            Vector2f cTwo = new Vector2f(faceTwo.vertices[2].initialLightmapCoords.x, faceTwo.vertices[2].initialLightmapCoords.y);
            float heightTwo = aTwo.distance(cTwo);
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
            Mesh.CompiledFace currentFace = sortedByLength.get(i);
            Vector2f a = new Vector2f(currentFace.vertices[0].initialLightmapCoords.x, currentFace.vertices[0].initialLightmapCoords.y);
            Vector2f b = new Vector2f(currentFace.vertices[1].initialLightmapCoords.x, currentFace.vertices[1].initialLightmapCoords.y);
            Vector2f c = new Vector2f(currentFace.vertices[2].initialLightmapCoords.x, currentFace.vertices[2].initialLightmapCoords.y);
            Vector2f widthVector = new Vector2f(a).sub(b);
            Vector2f heightVector = new Vector2f(a).set(c);
            float width = widthVector.length();
            float height = heightVector.length();
            if(width < 1.0f) {
                a.add(widthVector.normalize(), b);
            } else if(width > 4f) {
                a.add(widthVector.normalize().mul(10.0f), b);
            }
            if(height < 1.0f) {
                a.add(heightVector.normalize(), c);
            } else if(width > 4f) {
                a.add(heightVector.normalize().mul(10.0f), c);
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

    public void setTheNewValues(int currentWidth, int currentHeight, List<Vector3f> copy, Mesh.CompiledFace currentFace, float width, float height) {
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
                        xPoints[0] = (int) (copy.get(i).x() * this.getWidth() / MAX_WIDTH);
                        xPoints[1] = (int) (copy.get(i+1).x() * this.getWidth() / MAX_WIDTH);
                        xPoints[2] = (int) (copy.get(i+2).x() * this.getWidth() / MAX_WIDTH);

                        yPoints[0] = (int) (copy.get(i).y() * this.getHeight() / MAX_HEIGHT);
                        yPoints[1] = (int) (copy.get(i+1).y() * this.getHeight() / MAX_HEIGHT);
                        yPoints[2] = (int) (copy.get(i+2).y() * this.getHeight() / MAX_HEIGHT);
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
