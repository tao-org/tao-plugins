import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.aws.AWSDataSource;
import ro.cs.tao.datasource.remote.aws.download.AWSStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.landsat.LandsatProduct;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class AWSDataSourceTest {

    public static void main(String[] args) {
//        test_ping();
        Sentinel2_Test();
//        Landsat8_Test();
    }

    public static void test_ping() {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource dataSource = serviceRegistry.getService(AWSDataSource.class);
        dataSource.setCredentials("accessKeyId", "secretAccessKey");
        if (dataSource.ping()) {
            System.out.println("Ping successful");
        } else {
            System.out.println("Ping failed");
        }
    }

    public static void Sentinel2_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(AWSDataSource.class);
            dataSource.setCredentials("accessKeyId", "secretAccessKey");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            //query.addParameter("platformName", "S2");
            QueryParameter<LocalDateTime> begin = query.createParameter("startDate", LocalDateTime.class);
            begin.setMinValue(LocalDateTime.of(2019, 8, 1, 0, 0, 0, 0));
            begin.setMaxValue(LocalDateTime.of(2019, 10, 17, 0, 0, 0, 0));
            query.addParameter(begin);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                    "24.83885442747927 43.8379609098684," +
                    "24.83885442747927 44.795645304033826," +
                    "22.8042573604346 44.795645304033826," +
                    "22.8042573604346 43.8379609098684))");
            query.addParameter("footprint", aoi);

            query.addParameter("cloudCover", 100.);
            query.setPageSize(10);
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

            final AWSStrategy strategy = (AWSStrategy) dataSource.getProductFetchStrategy(sensors[0]);
//            strategy.setProgressListener(new ProgressNotifier(SystemPrincipal.instance(), dataSource, Topics.PROGRESS));

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

    public static void Landsat8_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(AWSDataSource.class);
            String[] sensors = dataSource.getSupportedSensors();
            DataQuery query = dataSource.createQuery(sensors[1]);
            //query.addParameter("platformName", "Landsat8");
            QueryParameter<LocalDateTime> begin = query.createParameter("startDate", LocalDateTime.class);
            begin.setValue(LocalDateTime.now().minusDays(60));
            query.addParameter(begin);
            Polygon2D aoi = new Polygon2D();
            aoi.append(-9.9866909768, 23.4186029838);
            aoi.append(-8.9037319257, 23.4186029838);
            aoi.append(-8.9037319257, 24.413397299);
            aoi.append(-9.9866909768, 24.413397299);
            aoi.append(-9.9866909768, 23.4186029838);
            query.addParameter("footprint", aoi);
            //query.addParameter("collection", "COLLECTION_1");
            query.addParameter("cloudCover", 100.);
            query.addParameter("productType", LandsatProduct.T1.toString());
            query.setPageSize(50);
            query.setMaxResults(83);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes().forEach(a -> System.out.println("\tName='" + a.getName() + "', value='" + a.getValue() + "'"));
            });

            final AWSStrategy strategy = (AWSStrategy) dataSource.getProductFetchStrategy(sensors[1]);
//            strategy.setProgressListener(new ProgressNotifier(SystemPrincipal.instance(), dataSource, Topics.PROGRESS));

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
