package de.hanno.hpengine.util.gui.structure;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.TubeLight;
import de.hanno.hpengine.util.Util;

import javax.swing.table.AbstractTableModel;

public class TubeLightsTableModel extends AbstractTableModel {

    public int getColumnCount() {
        return 3;
    }

    public int getRowCount() {
        if(Engine.getInstance().getSceneManager().getScene() != null) {
            return Engine.getInstance().getSceneManager().getScene().getTubeLights().size();
        }
        return 0;
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) {
            TubeLight light = Engine.getInstance().getSceneManager().getScene().getTubeLights().get(row);
            return String.format("%s (Range %f)", light.getName(), light.getScale().x);

        } else if (col == 1) {
            return Util.vectorToString(Engine.getInstance().getSceneManager().getScene().getTubeLights().get(row).getPosition());

        } else if (col == 2) {
            return Util.vectorToString(Engine.getInstance().getSceneManager().getScene().getTubeLights().get(row).getColor());

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
