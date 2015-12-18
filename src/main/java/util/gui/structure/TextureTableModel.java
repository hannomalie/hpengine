package util.gui.structure;

import texture.TextureFactory;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class TextureTableModel extends AbstractTableModel {

    public int getColumnCount() {
        return 2;
    }

    public int getRowCount() {
        return TextureFactory.getInstance().TEXTURES.size();
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) {
            List<Object> paths = Arrays.asList(TextureFactory.getInstance().TEXTURES.keySet()
                    .toArray());
            return paths.get(row);
        }
        List<Object> textures = Arrays.asList(TextureFactory.getInstance().TEXTURES.values()
                .toArray());
        texture.Texture texture = (texture.Texture) textures.get(row);
        return String.format("Texture %d x %d", texture.getWidth(), texture.getHeight());
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "Path";
        } else if (column == 1) {
            return "Texture";
        }
        return "Null";
    }

}
