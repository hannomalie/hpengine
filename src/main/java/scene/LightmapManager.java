package scene;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

public class LightmapManager {

    public static LightmapManager instance = new LightmapManager();
    private final SliceHolder sliceHolder;

    static final float PADDING = 1;
    public static final float MIN_SIZE = 2f;

    public LightmapManager() {
        sliceHolder = new SliceHolder();
    }

    public List<Vector3f[]> add(List<Vector3f[]> lightMapCoords) {
        return sliceHolder.add(lightMapCoords);
    }

    public float getWidth() {
        return sliceHolder.getWidth();
    }
    public float getHeight() {
        return sliceHolder.getHeight();
    }
    public void showLightmap() {
        sliceHolder.showLightmap();
    }

    public static LightmapManager getInstance() {
        return instance;
    }

    private static class SliceHolder {
        private ArrayList copy = new ArrayList();
        private List<XSlice> slices = new ArrayList<>();
        private List allLightmapCoordinates = new ArrayList();

        public List<Vector3f[]> add(List<Vector3f[]> lightMapCoords) {
            List<Vector3f[]> result = new ArrayList<>(lightMapCoords.size());

            for(Vector3f[] face : lightMapCoords) {
                Vector3f[] minMax = getMinMax(face);
                Vector2f size = new Vector2f(minMax[1].x - minMax[0].x, minMax[1].y - minMax[0].y);
                Vector3f min = new Vector3f(minMax[0]);
                boolean recalculate = false;
                for(int i = 0; i < 3; i++) {
                    if(face[i].x != min.x) {
                        if(face[i].x < MIN_SIZE) { face[i].x = MIN_SIZE; recalculate = true; }
                    }
                    if(face[i].y != min.y) {
                        if(face[i].y < MIN_SIZE) { face[i].y = MIN_SIZE; recalculate = true; }
                    }
                }
//                Face corrupt....
//                if(face[0].equals(face[1])) { System.out.println("XXXXXXXXXXXX"); }
//                if(face[0].equals(face[2])) { System.out.println("XXXXXXXXXXXX"); }
//                if(face[1].equals(face[2])) { System.out.println("XXXXXXXXXXXX"); }

                if(recalculate) {
                    minMax = getMinMax(face);
                    min = new Vector3f(minMax[0]);
                    size = new Vector2f(minMax[1].x - minMax[0].x, minMax[1].y - minMax[0].y);
                }

                List<XSlice> slicesWithSufficientHeight = new ArrayList<>();
                for(XSlice slice : slices) {
                    if(slice.height >= size.y) {
                        slicesWithSufficientHeight.add(slice);
                    }
                }

                Vector2f offset;
                if(slicesWithSufficientHeight.isEmpty()) {
                    XSlice newSlice = new XSlice(getCurrentYOffset(), size.getY());
                    slices.add(newSlice);
                    offset = newSlice.add(size);
                } else {
                    XSlice bestCandidate = null;
                    float minWidth = Float.MAX_VALUE;
                    for(XSlice slice : slicesWithSufficientHeight) {
                        if(slice.getCurrentWidth() < minWidth) {
                            minWidth = slice.getCurrentWidth();
                            bestCandidate = slice;
                        }
                    }
                    offset = bestCandidate.add(size);
                }

                Vector3f[] faceResult = new Vector3f[3];
                for(int i = 0; i < 3; i++) {
                    faceResult[i] = new Vector3f();
                    Vector3f.add(face[i], new Vector3f(offset.x, offset.y, 0), faceResult[i]);

                    faceResult[i].x += Math.abs(min.x);
                    faceResult[i].y += Math.abs(min.y);

                }
                result.add(faceResult);
            }

            for(Vector3f[] source : result) {
                copy.add(new Vector3f(source[0]));
                copy.add(new Vector3f(source[1]));
                copy.add(new Vector3f(source[2]));
            }
            allLightmapCoordinates.addAll(copy);
            return result;
        }

        public Vector3f[] getMinMax(Vector3f[] face) {
            Vector3f[] minMax = new Vector3f[2];
            minMax[0] = new Vector3f(face[0]);
            minMax[1] = new Vector3f(face[0]);
            for(int i = 0; i < 3; i++) {
                minMax[0].x = face[i].x < minMax[0].x ? face[i].x : minMax[0].x;
                minMax[0].y = face[i].y < minMax[0].y ? face[i].y : minMax[0].y;

                minMax[1].x = face[i].x > minMax[1].x ? face[i].x : minMax[1].x;
                minMax[1].y = face[i].y > minMax[1].y ? face[i].y : minMax[1].y;

            }
            return minMax;
        }

        private float getCurrentYOffset() {
            if(slices.isEmpty()) { return 0; }
            XSlice latestSlice = slices.get(slices.size() - 1);
            float currentOffset = latestSlice.yOffset + latestSlice.height;
            return currentOffset + PADDING;
        }

        public float getWidth() {
            float currentMax = 0;
            if(slices.isEmpty()) { return 1; }
            for(XSlice slice : slices) {
                if(slice.getCurrentWidth() > currentMax) {
                    currentMax = slice.getCurrentWidth();
                }
            }

            return currentMax;
        }

        public float getHeight() {
            if(slices.isEmpty()) { return 1; }
            XSlice latestSlice = slices.get(slices.size() - 1);
            return latestSlice.yOffset + latestSlice.height + PADDING;
        }

        public void showLightmap() {
            SwingUtilities.invokeLater(() -> {
                LightmapFrame frame = new LightmapFrame(copy);
                frame.setVisible(true);
            });
        }
    }

    private static class XSlice {
        float currentWidth = 0f;
        float yOffset;
        final float height;

        public XSlice(float yOffset, float height) {
            this.yOffset = yOffset;
            this.height = height;
        }

        public Vector2f add(Vector2f sizeOfRectangle) {
            Vector2f vector2f = new Vector2f(currentWidth, yOffset);
            currentWidth += sizeOfRectangle.x + PADDING;
            return vector2f;
        }

        public float getCurrentWidth() {
            return currentWidth;
        }
    }
    private static class LightmapFrame extends JFrame {

        private final List<Vector3f> copy;

        public LightmapFrame(List<Vector3f> copy){
            this.copy = copy;
            this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            JPanel panel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {

                    super.paintComponent(g);
                    for(int i = 0; i < copy.size(); i+=3) {
                        int[] xPoints = new int[3];
                        int[] yPoints = new int[3];
                        xPoints[0] = (int) (copy.get(i).getX() * 1280 / LightmapManager.getInstance().getWidth());
                        xPoints[1] = (int) (copy.get(i+1).getX() * 1280 / LightmapManager.getInstance().getWidth());
                        xPoints[2] = (int) (copy.get(i+2).getX() * 1280 / LightmapManager.getInstance().getWidth());
                        yPoints[0] = (int) (copy.get(i).getY() * 720 / LightmapManager.getInstance().getHeight());
                        yPoints[1] = (int) (copy.get(i+1).getY() * 720 / LightmapManager.getInstance().getHeight());
                        yPoints[2] = (int) (copy.get(i+2).getY() * 720 / LightmapManager.getInstance().getHeight());
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

    }
}
