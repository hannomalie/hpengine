package de.hanno.hpengine.util.gui.structure;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ComponentMapper;
import de.hanno.hpengine.engine.graphics.light.pointlight.PointLight;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.util.Util;

import javax.swing.table.AbstractTableModel;

public class PointLightsTableModel extends AbstractTableModel {

    ComponentMapper<PointLight> mapper = ComponentMapper.Companion.forClass(PointLight.class);
    private Engine engine;

    public PointLightsTableModel(Engine engine) {
        this.engine = engine;
    }

    public int getColumnCount() {
        return 3;
    }

    public int getRowCount() {
        Scene scene = engine.getSceneManager().getScene();
        return scene.getPointLights().size();
    }

    public Object getValueAt(int row, int col) {
        PointLight light = engine.getSceneManager().getScene().getPointLights().get(row);
        if (col == 0) {
            return String.format("%s (Range %f)", light.getEntity().getName(), light.getEntity().getScale().x);

        } else if (col == 1) {
            return Util.vectorToString(light.getEntity().getPosition());

        } else if (col == 2) {
            return Util.vectorToString(light.getColor());

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
