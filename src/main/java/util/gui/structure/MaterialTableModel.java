package util.gui.structure;

import renderer.material.Material;
import renderer.material.MaterialFactory;
import texture.Texture;
import texture.TextureFactory;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MaterialTableModel extends AbstractTableModel {

    public int getColumnCount() {
        return 1;
    }

    public int getRowCount() {
        try {
            return MaterialFactory.getInstance().MATERIALS.size();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public Material getValueAt(int row, int col) {
        if (col == 0) {
            return getMaterial(row);
        }
        throw new IllegalArgumentException("Column index should be 0");
    }

    private Material getMaterial(int row) {
        return MaterialFactory.getInstance().MATERIALS.values()
                .stream()
                .sorted((one, two) -> one.getName().compareTo(two.getName()))
                .collect(Collectors.toList())
                .get(row);
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "Material";
        }
        throw new IllegalArgumentException("Column index should be 0");
    }

}
