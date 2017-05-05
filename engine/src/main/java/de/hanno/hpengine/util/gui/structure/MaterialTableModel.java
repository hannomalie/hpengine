package de.hanno.hpengine.util.gui.structure;


import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;

import javax.swing.table.AbstractTableModel;
import java.util.Comparator;
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
        return MaterialFactory.getInstance().MATERIALS
                .stream()
                .sorted(Comparator.comparing(Material::getName))
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
