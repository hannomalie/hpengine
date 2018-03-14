package de.hanno.hpengine.util.gui.structure;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.util.Util;

import javax.swing.table.AbstractTableModel;

public class TubeLightsTableModel extends AbstractTableModel {

    private Engine engine;

    public TubeLightsTableModel(Engine engine) {
        this.engine = engine;
    }

    public int getColumnCount() {
        return 3;
    }

    public int getRowCount() {
        return engine.getSceneManager().getScene().getTubeLights().size();
    }

    public Object getValueAt(int row, int col) {
        TubeLight light = engine.getSceneManager().getScene().getTubeLights().get(row);
        Entity entity = light.getEntity();
        if (col == 0) {
            return String.format("%s (Range %f)", light.getEntity().getName(), light.getLength());

        } else if (col == 1) {
            return Util.vectorToString(entity.getPosition());

        } else if (col == 2) {
            return Util.vectorToString(engine.getSceneManager().getScene().getTubeLights().get(row).getColor());

        }
        return "";
    }

    public String getColumnName(int column) {
        if (column == 0) {
            return "Name";
        } else if (column == 1) {
            return "Position";
        } else if (column == 2) {
            return "Color";
        }
        return "Null";
    }
}
