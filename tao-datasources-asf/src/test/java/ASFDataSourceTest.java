import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.asf.ASFDataSource;
import ro.cs.tao.datasource.remote.asf.download.AsfDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Valentin Netoiu
 */
public class ASFDataSourceTest {

    public static void main(String[] args) {
        //test_ping();
        Sentinel1_Test();
        //Sentinel1_Filter_Test();
    }

    public static void test_ping() {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource<?, ?> dataSource = serviceRegistry.getService(ASFDataSource.class);
        dataSource.setCredentials("vnetoiu", "ASF_x2019");
        if (dataSource.ping()) {
            System.out.println("Ping successful");
        } else {
            System.out.println("Ping failed");
        }
    }

    public static void Sentinel1_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource<?, ?> dataSource = serviceRegistry.getService(ASFDataSource.class);
            dataSource.setCredentials("vnetoiu", "ASF_x2019");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            QueryParameter<LocalDateTime> begin = query.createParameter("startDate", LocalDateTime.class);
            begin.setValue(LocalDateTime.of(2022, 4, 17, 0, 0, 0, 0));
            query.addParameter(begin);
            QueryParameter<LocalDateTime> end = query.createParameter("endDate", LocalDateTime.class);
            begin.setValue(LocalDateTime.of(2022, 4, 18, 0, 0, 0, 0));
            query.addParameter(end);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((-12.810058 27.480984, -2.333497 27.480984, -2.333497 33.797409, -12.810058 33.797409, -12.810058 27.480984))");
            query.addParameter("footprint", aoi);
            //get OCN product type because the download file is small
            query.addParameter("productType", "SLC");

            query.setMaxResults(1);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes().forEach(a -> System.out.println("\tName='" + a.getName() + "', value='" + a.getValue() + "'"));
            });

            final AsfDownloadStrategy strategy = (AsfDownloadStrategy) dataSource.getProductFetchStrategy(sensors[0]);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Sentinel1_Filter_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource<?, ?> dataSource = serviceRegistry.getService(ASFDataSource.class);
            dataSource.setCredentials("vnetoiu", "ASF_x2019");
            String[] sensors = dataSource.getSupportedSensors();
            final Map<String, Map<String, Map<String, String>>> filteredParameters = new HashMap<>();
            filteredParameters.put(sensors[0], new HashMap<>());
            filteredParameters.get(sensors[0]).put("productType", new HashMap<>());
            final Map<String, String> productType = filteredParameters.get(sensors[0]).get("productType");
            // "METADATA_GRD", "GRD_HS", "GRD_HD", "GRD_MS", "GRD_MD", "GRD_FS", "GRD_FD", "SLC", "RAW", "OCN", "METADATA_RAW", "METADATA", "METADATA_SLC", "THUMBNAIL"
            productType.put("GRD_HS", "GRD_HS");
            productType.put("GRD_HD", "GRD_HD");
            productType.put("GRD_MS", "GRD_MS");
            productType.put("GRD_MD", "GRD_MD");
            productType.put("GRD_FS", "GRD_FS");
            productType.put("GRD_FD", "GRD_FD");
            productType.put("SLC", "SLC");
            productType.put("OCN", "Funny name");
            System.out.println(JsonMapper.instance().writer().writeValueAsString(filteredParameters));
            dataSource.setFilteredParameters(filteredParameters);
            final Map<String, Map<String, DataSourceParameter>> supportedParameters = dataSource.getSupportedParameters();
            final Map<String, DataSourceParameter> parameterMap = supportedParameters.get(sensors[0]);
            final StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, DataSourceParameter> entry : parameterMap.entrySet()) {
                builder.append(entry.getKey()).append(": [");
                final Object[] valueSet = entry.getValue().getValueSet();
                if (valueSet != null) {
                    builder.append(Arrays.stream(valueSet).map(Object::toString).collect(Collectors.joining(",")));
                } else {
                    builder.append("no valueSet");
                }
                builder.append("]\n");
            }
            System.out.println(builder.toString());
            DataQuery query = dataSource.createQuery(sensors[0]);
            QueryParameter<LocalDateTime> begin = query.createParameter("startDate", LocalDateTime.class);
            begin.setValue(LocalDateTime.of(2019, 8, 1, 0, 0, 0, 0));
            query.addParameter(begin);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((20.928955 43.213214," +
                                                      "30.953979 43.213214," +
                                                      "30.953979 48.886419," +
                                                      "20.928955 48.886419," +
                                                      "20.928955 43.213214))");
            query.addParameter("footprint", aoi);
            //get OCN product type because the download file is small
            query.addParameter("productType", "Funny name");

            query.setMaxResults(1);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes().forEach(a -> System.out.println("\tName='" + a.getName() + "', value='" + a.getValue() + "'"));
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
