package renderer.command;

import component.ModelComponent;
import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import org.lwjgl.util.vector.Vector3f;
import renderer.command.LoadModelCommand.EntityListResult;

import javax.swing.*;
import javax.vecmath.Vector4f;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LoadModelCommand implements Command<EntityListResult> {
    private final File file;
    private final String name;

    public LoadModelCommand(File file, String name) {
        this.file = file;
        this.name = name;
    }

    public EntityListResult execute(AppContext appContext) {
        EntityListResult result = new EntityListResult();
        try {
            List<Model> models = new OBJLoader().loadTexturedModel(file);
            List<Entity> entities = new ArrayList<>();
            entities.addAll(EntityFactory.getInstance().getEntity(name, models).getAllChildrenAndSelf());

//            showCoordsFrame(entities);

            return new EntityListResult(entities);

        } catch (IOException e) {
            e.printStackTrace();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return result;
        }
    }

    public void showCoordsFrame(List<Entity> entities) {
        int scale = 100;
        List<Vector3f> coords = new ArrayList();
        List<Integer> entityIndices = new ArrayList();
        List<Vector3f> colors = new ArrayList();
        int i = 0;
        float inverseEntityCount = 1f/entities.size();
        for(Entity entity : entities) {
            ModelComponent component = entity.getComponent(ModelComponent.class);
            if(component != null) {
                List<Vector3f> lightmapTexCoords = component.getModel().getLightmapTexCoords();
                for(Vector3f tempCoords : lightmapTexCoords) {
                    tempCoords.x = tempCoords.x * scale;
                    tempCoords.y = tempCoords.y * scale;
                    entityIndices.add(i);
                    colors.add(new Vector3f(inverseEntityCount*i, inverseEntityCount*i, inverseEntityCount*i));
                }
                coords.addAll(lightmapTexCoords);
                i++;
            }
        }
        EventQueue.invokeLater(() -> {
            JFrame before = new JFrame("before");
            before.setPreferredSize(new Dimension(1920, 1080));
            before.setSize(new Dimension(1920, 1080));
            before.add(new JPanel() {
                @Override
                public void paintComponent(Graphics g)
                {
                    super.paintComponent(g);
                    for(int i = 0; i < coords.size(); i+=3) {
                        int x1 = (int) coords.get(i+0).x + scale*entityIndices.get(i+0);
                        int y1 = (int) coords.get(i+0).y;// + scale*(int)coords.get(i+0).z;
                        int x2 = (int) coords.get(i+1).x + scale*entityIndices.get(i+1);
                        int y2 = (int) coords.get(i+1).y;// + scale*(int)coords.get(i+1).z;
                        int x3 = (int) coords.get(i+2).x + scale*entityIndices.get(i+2);
                        int y3 = (int) coords.get(i+2).y;// + scale*(int)coords.get(i+2).z;
                        g.setColor(new Color(colors.get(i).x, colors.get(i).y, colors.get(i).z));
                        g.drawLine(x1, y1, x2, y2);
                        g.drawLine(x1, y1, x3, y3);
                        g.drawLine(x2, y2, x3, y3);
                    }
                }
            });
            before.setVisible(true);
        });
    }

    public static class EntityListResult extends Result {

        public List<Entity> entities;
        private boolean successFul = false;

        public EntityListResult() {
        }

        public EntityListResult(List<Entity> entities) {
            this.entities = entities;
            this.successFul = true;
        }

        public void setEntities(List<Entity> entities) {
            this.entities = entities;
            this.successFul = true;
        }

        @Override
        public boolean isSuccessful() {
            return successFul;
        }

    }
}
