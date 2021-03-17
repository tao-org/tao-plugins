import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOData;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateProductList {

    public static void main(String[] args) throws Exception {
        final int year = 2018;
        Path destination = Paths.get(String.format("C:\\ssd\\s1_products_%d.txt", year));
        Files.deleteIfExists(destination);
        Files.createFile(destination);
        final List<String> tiles = new ArrayList<String>() {{
           add("17MNU"); add("17MPU"); add("17MQU"); add("17MRU");
        }};
        Polygon2D footprint;
        Set<String> results = new TreeSet<>();
        for (String tile : tiles) {
            System.out.println("Querying for tile " + tile);
            footprint = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(tile));
            results.addAll(doSciHubQuery(year, footprint.toWKT(), "Sentinel-1", "SLC"));
            Files.write(destination, (String.join("\n", results)).getBytes(), StandardOpenOption.APPEND);
        }
        System.exit(0);
    }

    private static Connection openConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/sen4cap", "admin", "admin");
    }

    private static List<String> doSciHubQuery(int year, String footprint, String satellite, String productType) {
        List<String> products = new ArrayList<>();
        try {
            final DataSource<?, ?> dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("kraftek", "cei7samurai");
            final DataQuery query = dataSource.createQuery(satellite.replace("-", ""));
            query.addParameter(CommonParameterNames.PLATFORM, satellite);
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, productType);
            final QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
            begin.setMinValue(Date.from(LocalDateTime.of(year, 1, 1, 0, 0, 0, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()));
            begin.setMaxValue(Date.from(LocalDateTime.of(year, 12, 31, 23, 59, 59, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()));
            query.addParameter(begin);
            final Polygon2D aoi = Polygon2D.fromWKT(footprint);
            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
            query.setPageSize(50);
            query.setMaxResults(1000);
            products.addAll(query.execute().stream().map(EOData::getName).collect(Collectors.toList()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
