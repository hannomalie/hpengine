package de.hanno.hpengine.util.gui.structure;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.texture.OpenGlTexture;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class TextureTableModel extends AbstractTableModel {

    private Engine engine;

    public TextureTableModel(Engine engine) {
        this.engine = engine;
    }

    public int getColumnCount() {
        return 4;
    }

    public int getRowCount() {
        try {
            return engine.getTextureManager().getTextures().size();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) {
            List<Object> paths = Arrays.asList(engine.getTextureManager().getTextures().keySet()
                    .toArray());
            return paths.get(row);
        } else if(col == 1) {
            de.hanno.hpengine.engine.model.texture.OpenGlTexture texture = getTexture(row);
            return String.format("Texture %d x %d", texture.getWidth(), texture.getHeight());
        } else if(col == 2) {
            de.hanno.hpengine.engine.model.texture.OpenGlTexture texture = getTexture(row);
            return texture.getUploadState();
        } else if(col == 3) {
            de.hanno.hpengine.engine.model.texture.OpenGlTexture texture = getTexture(row);
            return 0; // TODO: Use this colum  here somehow
        }
        return "";
    }

    private OpenGlTexture getTexture(int row) {
        List<Object> textures = Arrays.asList(engine.getTextureManager().getTextures().values()
                .toArray());
        return (OpenGlTexture) textures.get(row);
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "Path";
        } else if (column == 1) {
            return "Texture";
        } else if (column == 2) {
            return "State";
        } else if (column == 3) {
            return "Used ms ago";
        }
        return "Null";
    }

}
