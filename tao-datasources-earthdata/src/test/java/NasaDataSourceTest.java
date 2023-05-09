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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class NasaDataSourceTest {

    private static final QueryParameter<LocalDateTime> begin;
    private static final QueryParameter<LocalDateTime> end;
    private static final QueryParameter<Polygon2D> aoi;

    static {
        begin = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.START_DATE);
        end = new QueryParameter<>(LocalDateTime.class, CommonParameterNames.END_DATE);
        begin.setMinValue(LocalDateTime.of(2020, 2, 1, 0, 0, 0, 0));
        begin.setMaxValue(LocalDateTime.of(2020, 2, 2, 23, 59, 59, 0));
//            begin.setValue(Date.from(LocalDateTime.of(2020, 2, 1, 0, 0, 0, 0)
//                    .atZone(ZoneId.systemDefault())
//                    .toInstant()));
        end.setMinValue(LocalDateTime.of(2020, 2, 1, 0, 0, 0, 0));
        end.setMaxValue(LocalDateTime.of(2020, 2, 2, 23, 59, 59, 0));
//            end.setValue(Date.from(LocalDateTime.of(2020, 2, 2, 23, 59, 59, 0)
//                    .atZone(ZoneId.systemDefault())
//                    .toInstant()));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((23.76563 6.07764,22.35938 3.40672,27 0.59522,28.96875 5.09362,27.28125 9.31086,23.76563 6.07764))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
    }

    public static void main(String[] args) {
        downloadH5();
//        downloadH5IceSAT2();
//        downloadGedi();
    }

    public static void downloadH5(){
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(EarthDataSource.class);

            dataSource.setCredentials("lidia901", "SMAPassword1");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[1]);

//            query.addParameter(CommonParameterNames.TILE, "*_20210117_*");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("APPROXIMATE SIZE=" + r.getApproximateSize());
                System.out.println("PRODUCT TYPE=" + r.getProductType());
            });

            final EarthDataDownloadStrategy strategy = (EarthDataDownloadStrategy) dataSource.getProductFetchStrategy(sensors[0]);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination("E:\\testTAO");
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        }catch ( Exception e){
            e.printStackTrace();
        }
    }

    public static void downloadH5IceSAT2(){
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(EarthDataSource.class);

            dataSource.setCredentials("lidia901", "SMAPassword1");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("IceSAT-2-ATL08V3");

//            query.addParameter("equatorCrossingDate", "2020-11-01T00:00:00.000Z,2020-11-13T23:59:59.999Z");
            query.addParameter("equatorCrossingLongMin",0);
            query.addParameter("equatorCrossingLongMax",3);
            query.addParameter("orbitNumberMax",500);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("APPROXIMATE SIZE=" + r.getApproximateSize());
                System.out.println("PRODUCT TYPE=" + r.getProductType());
            });

            final EarthDataDownloadStrategy strategy = (EarthDataDownloadStrategy) dataSource.getProductFetchStrategy(sensors[0]);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination("E:\\testTAO");
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        }catch ( Exception e){
            e.printStackTrace();
        }
    }

    public static void downloadGedi(){
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
            DataSource dataSource = serviceRegistry.getService(EarthDataSource.class);

            dataSource.setCredentials("lidia901", "SMAPassword1");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("GEDI");

            query.setMaxResults(40);
//            query.addParameter(CommonParameterNames.TILE, "*_2019108015252_*");

            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);

            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("APPROXIMATE SIZE=" + r.getApproximateSize());
                System.out.println("PRODUCT TYPE=" + r.getProductType());
            });

            final EarthDataDownloadStrategy strategy = (EarthDataDownloadStrategy) dataSource.getProductFetchStrategy(sensors[0]);

            if (!results.isEmpty()) {
                strategy.setFetchMode(FetchMode.OVERWRITE);
                strategy.setDestination("E:\\testTAO");
                Path path = strategy.fetch(results.get(0));
                if (path != null) {
                    System.out.println("Product downloaded at " + path.toString());
                } else {
                    System.out.println("Product not downloaded");
                }
            }
        }catch ( Exception e){
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
