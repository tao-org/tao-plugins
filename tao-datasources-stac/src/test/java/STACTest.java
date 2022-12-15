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
                return new String[] { "sentinel-2-l2a" };
            }

            @Override
            protected void readInfo() {
                this.serviceURL = "https://earth-search.aws.element84.com/v1/";
                this.authentication = null;
            }
        };
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery(sensors[0]);
        QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
        begin.setValue(LocalDateTime.of(2020, 11, 1, 0, 0, 0, 0));
        query.addParameter(begin);
        QueryParameter<LocalDateTime> end = query.createParameter(CommonParameterNames.END_DATE, LocalDateTime.class);
        end.setValue(LocalDateTime.of(2020, 12, 31, 0, 0, 0, 0));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((0.5688037328 42.426901687,1.9031483158 42.4474430295,1.9199728398 41.4586816132,0.6060608364 41.438836285,0.5688037328 42.426901687))");
        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.setPageSize(20);
        query.setPageNumber(2);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
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
