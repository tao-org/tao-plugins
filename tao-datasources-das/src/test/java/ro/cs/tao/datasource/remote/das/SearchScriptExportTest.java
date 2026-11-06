package ro.cs.tao.datasource.remote.das;

import org.junit.Test;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.eodata.Polygon2D;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchScriptExportTest {
    @Test
    public void buildsProviderUrlWithoutExecutingSearch() throws Exception {
        DataQuery query = new DASDataSource().createQuery("Sentinel1");
        query.addParameter("footprint", Polygon2D.class,
                           Polygon2D.fromWKT("POLYGON((20 44,21 44,21 45,20 45,20 44))"));
        query.addParameter("startDate", LocalDateTime.class, LocalDateTime.of(2024, 1, 1, 0, 0));
        query.addParameter("endDate", LocalDateTime.class, LocalDateTime.of(2024, 1, 31, 23, 59));
        query.setMaxResults(5);
        query.setPageSize(3);
        query.setPageNumber(2);
        List<String> urls = query.exportSearchUrls();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "search-plan.json"),
                ro.cs.tao.serialization.JsonMapper.instance().writeValueAsString(query.exportSearchPlan()));
        assertFalse(urls.isEmpty());
        String url = URLDecoder.decode(urls.get(0), StandardCharsets.UTF_8);
        assertTrue(url.startsWith(query.getSource().getConnectionString("Sentinel1") + "?"));
        assertTrue(url.contains("2024-01-01"));
        assertTrue(url.contains("POLYGON"));
        assertTrue(url.contains("$top=3"));
        assertTrue(url.contains("$skip=3"));
    }
}
