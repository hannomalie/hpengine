package de.hanno.hpengine.util.gui.structure;


import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;

import javax.swing.table.AbstractTableModel;

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
            return engine.getScene().getMaterialManager().getMaterials().size();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public SimpleMaterial getValueAt(int row, int col) {
        if (col == 0) {
            return getMaterial(row);
        }
        throw new IllegalArgumentException("Column index should be 0");
    }

    private SimpleMaterial getMaterial(int row) {
        return engine.getScene().getMaterialManager().getMaterials()
                .get(row);
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "SimpleMaterial";
        }
        throw new IllegalArgumentException("Column index should be 0");
    }

}
