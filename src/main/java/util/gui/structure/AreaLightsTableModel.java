package util.gui.structure;

import engine.AppContext;
import renderer.light.AreaLight;

import javax.swing.table.AbstractTableModel;

import static util.Util.vectorToString;

public class AreaLightsTableModel extends AbstractTableModel {

    public int getColumnCount() {
        return 3;
    }

    public int getRowCount() {
        if(AppContext.getInstance().getScene() != null) {
            return AppContext.getInstance().getScene().getAreaLights().size();
        }
        return 0;
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) {
            AreaLight light = AppContext.getInstance().getScene().getAreaLights().get(row);
            return String.format("%s (Range %f)", light.getName(), light.getScale().z);

        } else if (col == 1) {
            return vectorToString(AppContext.getInstance().getScene().getAreaLights().get(row).getPosition());

        } else if (col == 2) {
            return vectorToString(AppContext.getInstance().getScene().getAreaLights().get(row).getColor());

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
