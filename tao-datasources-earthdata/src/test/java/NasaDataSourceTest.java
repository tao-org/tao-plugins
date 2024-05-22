import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.earthdata.EarthDataSource;
import ro.cs.tao.datasource.remote.earthdata.download.EarthDataDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class NasaDataSourceTest {

    private static final QueryParameter<LocalDateTime> begin;
    private static final QueryParameter<LocalDateTime> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final String token = "eyJ0eXAiOiJKV1QiLCJvcmlnaW4iOiJFYXJ0aGRhdGEgTG9naW4iLCJzaWciOiJlZGxqd3RwdWJrZXlfb3BzIiwiYWxnIjoiUlMyNTYifQ.eyJ0eXBlIjoiVXNlciIsInVpZCI6InZuZXRvaXUiLCJleHAiOjE3MjE1NTE4MzksImlhdCI6MTcxNjM2NzgzOSwiaXNzIjoiRWFydGhkYXRhIExvZ2luIn0.QgZSZf4NsqNanuHaJLDE3LGW9ShgFAdgRHj2vNL3-ahFwnUsTc7KY5bhj0BRtEHTgEdRM_Ak029uqhVOBtEoo5yIFa9qa5dYCihICio7mskE7ddBH7qHWOUz0Iq-yJQXBTWif1uOqUPeFsNxscsics-VQyvrJ4_fZhKr-Q1UGcJi-EzQrl--S-K-ipDFm6abhRYCT8tiTwOMz0a0EQOWNnbgf7-SBKAlGDHo4rzHlhxvY8v6JbGUQ05r0YwKdfWOHkanNMi0eqS1p1OxEI2pMGlRIL0RQ3DAuojJfDRabIvcOkOZ0G3IXf8BZDoCW-4hPTQ52arGX_9c5thCqEIE9g";

    static {
        begin = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.START_DATE);
        end = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.END_DATE);
        begin.setValue(LocalDateTime.of(2023, 3, 15, 0, 0, 0, 0));
        //begin.setMaxValue(LocalDateTime.of(2023, 2, 2, 23, 59, 59, 0));
//            begin.setValue(Date.from(LocalDateTime.of(2020, 2, 1, 0, 0, 0, 0)
//                    .atZone(ZoneId.systemDefault())
//                    .toInstant()));
        //end.setMinValue(LocalDateTime.of(2023, 2, 1, 0, 0, 0, 0));
        end.setValue(LocalDateTime.of(2023, 3, 17, 0, 0, 0, 0));
//            end.setValue(Date.from(LocalDateTime.of(2020, 2, 2, 23, 59, 59, 0)
//                    .atZone(ZoneId.systemDefault())
//                    .toInstant()));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((29.15771 38.50595,29.13574 37.71082,30.15527 37.724,30.1377 38.60699,29.15771 38.50595))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
    }

    public static void main(String[] args) {
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource dataSource = serviceRegistry.getService(EarthDataSource.class);
        final String[] sensors = dataSource.getSupportedSensors();
        for (String sensor : sensors) {
            if (sensor.startsWith("MODIS-CHL")) {
                searchAndDownload(sensor);
            }
        }
        System.exit(0);
    }

    public static void searchAndDownload(String collection){
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(EarthDataSource.class);

            dataSource.setCredentials("vnetoiu", "ASF_x2019");
            //String[] sensors = dataSource.getSupportedSensors();
            setTokenIfNeeded(dataSource, collection);
            DataQuery query = dataSource.createQuery(collection);

            query.setMaxResults(1);
//            query.addParameter(CommonParameterNames.TILE, "*_2019108015252_*");

            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setMaxResults(1);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("APPROXIMATE SIZE=" + r.getApproximateSize());
                System.out.println("PRODUCT TYPE=" + r.getProductType());
            });

            final EarthDataDownloadStrategy strategy = (EarthDataDownloadStrategy) dataSource.getProductFetchStrategy(collection);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination("E:\\eodata");
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        } catch ( Exception e){
            System.err.println("Downloading from " + collection + " failed: " + e.getMessage());
        }
    }

    private static void setTokenIfNeeded(DataSource dataSource, String sensor) {
        final Map<String, CollectionDescription> sensorTypes = dataSource.getSensorTypes();
        if (sensorTypes.get(sensor).isTokenNeeded()) {
            dataSource.setBearerToken(token);
        }
    }

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
