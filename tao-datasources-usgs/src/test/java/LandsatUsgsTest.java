import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.usgs.USGSDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.messaging.Message;
import ro.cs.tao.messaging.Messaging;
import ro.cs.tao.messaging.Notifiable;
import ro.cs.tao.messaging.ProgressNotifier;
import ro.cs.tao.messaging.Topics;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class LandsatUsgsTest {

    public static void main(String[] args) throws IOException {
        searchLandsat8Test();
    }

    static void searchLandsat8Test() throws IOException {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        ServiceRegistry<DataSource> serviceRegistry = getServiceRegistry();
        DataSource dataSource = serviceRegistry.getService(USGSDataSource.class);
        dataSource.setCredentials("kraftek", "cei7pitici.");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery(sensors[0]);
        QueryParameter<Date> begin = query.createParameter("date_from", Date.class);
        begin.setValue(Date.from(LocalDateTime.of(2016, 2, 1, 0, 0, 0, 0)
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()));
        query.addParameter(begin);
        QueryParameter<Date> end = query.createParameter("date_to", Date.class);
        end.setValue(Date.from(LocalDateTime.of(2017, 2, 1, 0, 0, 0, 0)
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                  "24.83885442747927 43.8379609098684," +
                                                  "24.83885442747927 44.795645304033826," +
                                                  "22.8042573604346 44.795645304033826," +
                                                  "22.8042573604346 43.8379609098684))");

        query.addParameter("footprint", aoi);
        query.addParameter("cloud_to", 90.);
        query.setMaxResults(10);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
        final ProductFetchStrategy fetchStrategy = dataSource.getProductFetchStrategy(sensors[0]);
        fetchStrategy.setProgressListener(new ProgressNotifier(SystemPrincipal.instance(), dataSource, Topics.PROGRESS));

        Messaging.subscribe(new Notifiable() {
            @Override
            protected void onMessageReceived(Message message) {
                System.out.println(message.getData());
                message.setRead(true);
            }
        }, Topics.PROGRESS);

        final Path path = fetchStrategy.fetch(results.get(1));
        if (path != null) {
            System.out.println("Product downloaded at " + path.toString());
        } else {
            System.out.println("Product not downloaded");
        }
    }
    private static ServiceRegistry<DataSource> getServiceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
