package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.directory.AbstractDirectory;
import de.hanno.hpengine.engine.directory.GameDirectory;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand.EntityListResult;
import de.hanno.hpengine.engine.model.loader.md5.*;
import de.hanno.hpengine.engine.model.material.MaterialManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadModelCommand implements Command<EntityListResult> {
    private final File file;
    private final String name;
    private MaterialManager materialManager;
    private final GameDirectory gameDir;
    private Entity entity;

    public LoadModelCommand(File file, String name, MaterialManager materialManager, GameDirectory gameDir) {
        this(file, name, materialManager, gameDir, null);
    }
    public LoadModelCommand(File file, String name, MaterialManager materialManager, GameDirectory gameDir, Entity entity) {
        this.materialManager = materialManager;
        this.gameDir = gameDir;
        this.entity = entity;
        if(this.entity == null) {
            this.entity = new Entity(name);
        }
        if(file == null) {
            throw new IllegalArgumentException("Passed file is null!");
        }
        if(!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Passed file is nonexistent or a directory: " + file.getPath());
        }
        this.file = file;
        this.name = name;
    }

    public EntityListResult execute() {
        EntityListResult result = new EntityListResult();
        try {
            System.out.println("Loading model " +  name);
            long start = System.currentTimeMillis();

            List<Entity> entities = new ArrayList<>();
            Model model = getModel(materialManager, gameDir);
            ModelComponent modelComponent = new ModelComponent(entity, model, materialManager.getDefaultMaterial());
            entity.addComponent(modelComponent);

            entities.add(entity);
            EntityListResult entityListResult = new EntityListResult(entities);

            System.out.println("Loading took " + (System.currentTimeMillis() - start));
            return entityListResult;

        } catch (Exception e) {
            e.printStackTrace();
            return result;
        }
    }

    protected Model getModel(MaterialManager materialManager, AbstractDirectory textureDir) throws Exception {
        Model model;
        if(file.getAbsolutePath().endsWith("md5mesh")) {
            MD5Model parsedModel = MD5Model.parse(file.getAbsolutePath());
            MD5AnimModel parsedAnimModel = MD5AnimModel.parse(file.getAbsolutePath().replace("md5mesh", "md5anim"));
            model = MD5Loader.process(materialManager, parsedModel, parsedAnimModel, textureDir);
        } else {
            model = new OBJLoader().loadTexturedModel(materialManager, file);
        }
        return model;
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
