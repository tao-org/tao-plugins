import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.configuration.EmptyConfigurationProvider;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class STACTest {

    public static void main(String[] args) {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource<?, ?> dataSource = new STACSource("test") {

            @Override
            public String[] getSupportedSensors() {
                return new String[] { "SENTINEL-2" };
            }

            @Override
            protected void readInfo() {
                this.connectionString = "https://datahub.creodias.eu/stac/";
                this.authentication = null;
            }
        };
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery(sensors[0]);
        QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2023, 3, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        QueryParameter<LocalDateTime> end = query.createParameter(CommonParameterNames.END_DATE, LocalDateTime.class);
        end.setValue(LocalDateTime.of(2023, 3, 7, 23, 59, 59, 0));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((4.67 53.134,5.964 53.108,5.839 51.44,4.608 51.463,4.67 53.134))");
        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.setPageSize(20);
        query.setPageNumber(1);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
            System.out.println("QUICKLOOK=" + r.getQuicklookLocation());
        });
    }

    private static void setup() {
        ConfigurationProvider configurationProvider = new EmptyConfigurationProvider();
        final String userHome = System.getProperty("user.home");
        configurationProvider.setValue("products.location", userHome);
        configurationProvider.setScriptsFolder(Paths.get(userHome));
        configurationProvider.setConfigurationFolder(Paths.get(userHome));
        ConfigurationManager.setConfigurationProvider(configurationProvider);
    }
}
