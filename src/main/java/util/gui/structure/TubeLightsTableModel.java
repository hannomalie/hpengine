package util.gui.structure;

import engine.Engine;
import renderer.light.TubeLight;

import javax.swing.table.AbstractTableModel;

import static util.Util.vectorToString;

public class TubeLightsTableModel extends AbstractTableModel {

    public int getColumnCount() {
        return 3;
    }

    public int getRowCount() {
        if(Engine.getInstance().getScene() != null) {
            return Engine.getInstance().getScene().getTubeLights().size();
        }
        return 0;
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) {
            TubeLight light = Engine.getInstance().getScene().getTubeLights().get(row);
            return String.format("%s (Range %f)", light.getName(), light.getScale().x);

        } else if (col == 1) {
            return vectorToString(Engine.getInstance().getScene().getTubeLights().get(row).getPosition());

        } else if (col == 2) {
            return vectorToString(Engine.getInstance().getScene().getTubeLights().get(row).getColor());

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
