package ro.cs.tao.datasource.remote.earthdata.parameters;

import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.eodata.Polygon2D;

import java.awt.geom.Point2D;
import java.util.List;

public class PolygonParameterConverter extends DefaultParameterConverter {

    public PolygonParameterConverter(QueryParameter<?> parameter) {
        super(parameter);
        if (!Polygon2D.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Incorrect parameter type");
        }
    }

    @Override
    public String stringValue() {
        Polygon2D polygon2D = (Polygon2D) this.parameter.getValue();
        if (polygon2D != null && polygon2D.getNumPoints() > 0) {
            final StringBuilder polygonParam = new StringBuilder();
            List<Point2D> point2DList = polygon2D.getPoints();
            int numPoints = point2DList.size();
            for (int pointIndex = 0; pointIndex < numPoints; pointIndex++) {
                Point2D currentPoint = point2DList.get(pointIndex);
                polygonParam.append(currentPoint.getX()).append(",");
                polygonParam.append(currentPoint.getY());
                if (pointIndex != numPoints - 1) {
                    polygonParam.append(",");
                }
            }
            return polygonParam.toString();
        } else {
            return null;
        }
    }

}
