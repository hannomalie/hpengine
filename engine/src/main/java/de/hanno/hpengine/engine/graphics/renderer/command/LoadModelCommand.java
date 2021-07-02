package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.directory.AbstractDirectory;
import de.hanno.hpengine.engine.directory.GameDirectory;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand.EntityListResult;
import de.hanno.hpengine.engine.model.loader.assimp.AnimatedModelLoader;
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.model.texture.TextureManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadModelCommand implements Command<EntityListResult> {
    private final String file;
    private final String name;
    private TextureManager textureManager;
    private final AbstractDirectory gameDir;
    private Entity entity;

    public LoadModelCommand(String file, String name, TextureManager textureManager, GameDirectory gameDir) {
        this(file, name, textureManager, gameDir, null);
    }
    public LoadModelCommand(String file, String name, TextureManager textureManager, AbstractDirectory gameDir, Entity entity) {
        this.textureManager = textureManager;
        this.gameDir = gameDir;
        this.entity = entity;
        if(this.entity == null) {
            this.entity = new Entity(name);
        }
        if(file == null) {
            throw new IllegalArgumentException("Passed file is null!");
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
            Model model = getModel(textureManager, gameDir);
            ModelComponent modelComponent = new ModelComponent(entity, model, model.getMaterial());
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

    protected Model getModel(TextureManager textureManager, AbstractDirectory textureDir) throws Exception {
        Model model;
        if(file.endsWith("md5mesh")) {
            model = new AnimatedModelLoader().load(file, textureManager, gameDir);
        } else {
            model = new StaticModelLoader().load(file, textureManager, gameDir);
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
