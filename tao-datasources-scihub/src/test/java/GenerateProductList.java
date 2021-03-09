import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GenerateProductList {

    public static void main(String[] args) throws Exception {
        final Path source = Paths.get("C:\\Users\\kraftek\\Downloads\\data-1587051961378.csv");
        final int year = 2019;
        Path destination = Paths.get(String.format("D:\\S1\\s1_products_%d.txt", year));
        Files.deleteIfExists(destination);
        Files.createFile(destination);
        //final List<String> isoCodes = Files.lines(source).map(l -> l.substring(l.lastIndexOf(',') + 1).replace("\"", "")).collect(Collectors.toList());
        final List<String> isoCodes = new ArrayList<String>() {{
           add("GRL");
        }};
        try (final Connection connection = openConnection()) {
            final PreparedStatement statement = connection.prepareStatement("select st_astext(st_convexhull(geom)) from countries where \"iso3\" = ?");
            for (String isoCode : isoCodes) {
                System.out.print("Querying for " + isoCode);
                statement.setString(1, isoCode);
                final ResultSet resultSet = statement.executeQuery();
                String footprint = null;
                while (resultSet.next()) {
                    footprint = resultSet.getString(1);
                }
                final long count = doSciHubQuery(year, footprint, "Sentinel-1", "SLC");
                System.out.print(" returned " + count + " products\n");
                Files.write(destination, (count + ",").getBytes(), StandardOpenOption.APPEND);
            }
        }
        System.exit(0);
    }

    private static Connection openConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/sen4cap", "admin", "admin");
    }

    private static long doSciHubQuery(int year, String footprint, String satellite, String productType) {
        long result = 0;
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
            query.setPageSize(1);
            query.setMaxResults(1);
            result = query.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
