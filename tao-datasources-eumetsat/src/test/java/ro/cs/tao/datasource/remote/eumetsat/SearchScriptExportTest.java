package ro.cs.tao.datasource.remote.eumetsat;

import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.serialization.JsonMapper;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchScriptExportTest {
    @Test
    public void exportsResolvedMetadataWithoutNetworkOrDatabaseAccess() throws Exception {
        Map<String, DataSourceParameter> descriptors = new LinkedHashMap<>();
        descriptors.put("footprint", new DataSourceParameter("footprint", "geo", Polygon2D.class, "Area"));
        descriptors.put("startDate", new DataSourceParameter("startDate", "dtstart", LocalDateTime.class, "Start"));
        descriptors.put("endDate", new DataSourceParameter("endDate", "dtend", LocalDateTime.class, "End"));
        ParameterProvider provider = (ParameterProvider) Proxy.newProxyInstance(ParameterProvider.class.getClassLoader(),
                new Class<?>[] {ParameterProvider.class}, (proxy, method, args) -> {
                    if ("getSupportedSensors".equals(method.getName())) {
                        return new String[] {"collection"};
                    }
                    if ("getSupportedParameters".equals(method.getName())) {
                        return args == null || args.length == 0 ? Map.of("collection", descriptors) : descriptors;
                    }
                    return Map.of();
                });
        EumetsatDataSource source = new EumetsatDataSource("https://provider.example/search/", provider);
        DataQuery query = new EumetsatDataQuery(source, "collection");
        query.addParameter("footprint", Polygon2D.class,
                           Polygon2D.fromWKT("POLYGON((20 44,21 44,21 45,20 45,20 44))"));
        query.addParameter("startDate", LocalDateTime.class, LocalDateTime.of(2024, 1, 1, 0, 0));
        query.addParameter("endDate", LocalDateTime.class, LocalDateTime.of(2024, 1, 31, 23, 59));
        query.setMaxResults(5);
        query.setPageSize(3);
        query.setPageNumber(2);
        Map<String, Object> plan = query.exportSearchPlan();
        String json = JsonMapper.instance().writeValueAsString(plan);
        assertTrue(json.contains("provider.example"));
        assertFalse(json.contains("Polygon2D@"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "search-plan.json"), json);
    }
}
