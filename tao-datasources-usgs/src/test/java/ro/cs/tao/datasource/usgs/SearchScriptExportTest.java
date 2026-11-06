package ro.cs.tao.datasource.usgs;

import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.serialization.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.Assert.*;

public class SearchScriptExportTest {
    @Test
    public void preservesMultiplePathRowAlternatives() throws Exception {
        DataQuery query = new USGSDataSource().createQuery("Landsat8");
        query.addParameter("tileId", String[].class, new String[] {"190030", "191031"});
        String json = JsonMapper.instance().writeValueAsString(query.exportSearchPlan());
        assertTrue(json.contains("anyOf"));
        assertTrue(json.contains("190"));
        assertTrue(json.contains("191"));
        assertTrue(json.contains("030"));
        assertTrue(json.contains("031"));
    }

    @Test
    public void exportsCriteriaWithoutAuthenticationOrCredentials() throws Exception {
        USGSDataSource source = new USGSDataSource();
        String sensor = "Landsat8";
        DataQuery query = source.createQuery(sensor);
        source.setCredentials("secret-user", "secret-password");
        query.addParameter("footprint", Polygon2D.class,
                           Polygon2D.fromWKT("POLYGON((20 44,21 44,21 45,20 45,20 44))"));
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 31, 23, 59);
        DataSourceParameter date = query.getSupportedParameters().get("startDate");
        if (date.getType().isArray()) {
            query.addParameter(query.createParameter("startDate", date.getType(), start, end));
        } else {
            query.addParameter("startDate", LocalDateTime.class, start);
            query.addParameter("endDate", LocalDateTime.class, end);
        }
        query.setMaxResults(5);
        query.setPageSize(3);
        query.setPageNumber(2);
        query.addParameter("productType", "L1TP");
        Map<String, Object> plan = query.exportSearchPlan();
        assertEquals("usgs", plan.get("protocol"));
        String json = JsonMapper.instance().writeValueAsString(plan);
        assertTrue(json.contains("2024-01-01"));
        assertTrue(json.contains("2024-01-31"));
        assertFalse(json.contains("secret-user"));
        assertFalse(json.contains("secret-password"));
        assertFalse(json.contains("Polygon2D@"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "search-plan.json"), json);
    }
}
