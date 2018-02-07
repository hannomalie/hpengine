package de.hanno.hpengine.util.gui.structure;


import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.material.Material;

import javax.swing.table.AbstractTableModel;
import java.util.Comparator;
import java.util.stream.Collectors;

public class MaterialTableModel extends AbstractTableModel {

    private Engine engine;

    public MaterialTableModel(Engine engine) {
        this.engine = engine;
    }

    public int getColumnCount() {
        return 1;
    }

    public int getRowCount() {
        try {
            return engine.getMaterialManager().MATERIALS.size();
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
        return engine.getMaterialManager().MATERIALS
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
