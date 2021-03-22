import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.fedeo.FedEODataSource;
import ro.cs.tao.datasource.remote.fedeo.download.FedEODownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class FedEODataSourceTest {

    public static void main(String[] args) {
        test_ping();
        kompsat2_Test();
    }

    public static void test_ping() {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource dataSource = serviceRegistry.getService(FedEODataSource.class);
        if (dataSource.ping()) {
            System.out.println("Ping successful");
        } else {
            System.out.println("Ping failed");
        }
    }

    public static void kompsat2_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(FedEODataSource.class);
            dataSource.setCredentials("", "");
            String[] platforms = dataSource.getSupportedSensors();
            String platform = platforms[112];

            Map<String, Map<String, DataSourceParameter>> supportedParams = dataSource.getSupportedParameters();
            Map<String, DataSourceParameter> params = supportedParams.get(platform);

            String collection = (String) params.get("parentIdentifier").getValueSet()[0];
            DataQuery query = dataSource.createQuery(platform);
            query.addParameter(new QueryParameter<String>(String.class,"parentIdentifier", collection));
            QueryParameter<Date> dateInterval = query.createParameter(CommonParameterNames.START_DATE, Date.class);
            dateInterval.setMinValue(Date.from(LocalDateTime.of(2014, 3, 21, 0, 0, 0, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()));
            dateInterval.setMaxValue(Date.from(LocalDateTime.of(2014, 3, 22, 0, 0, 0, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()));
            query.addParameter(dateInterval);
            query.setPageSize(50);
            query.setPageNumber(1);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes().forEach(a -> System.out.println("\tName='" + a.getName() + "', value='" + a.getValue() + "'"));
            });

            final FedEODownloadStrategy strategy = (FedEODownloadStrategy) dataSource.getProductFetchStrategy(platform);

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

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
