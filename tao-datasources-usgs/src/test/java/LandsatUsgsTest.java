import ro.cs.tao.ProgressListener;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.usgs.USGSDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;

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

    public static void main(String[] args) throws Exception {
        searchLandsat8Test();
        searchNewLandsat8Test();
    }

    static void searchLandsat8Test() throws Exception {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource dataSource = new USGSDataSource();
        dataSource.setCredentials("kraftek", "cei7pitici.");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery(sensors[0]);
        QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
        begin.setValue(Date.from(LocalDateTime.of(2018, 7, 1, 0, 0, 0, 0)
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()));
        query.addParameter(begin);
        QueryParameter<Date> end = query.createParameter(CommonParameterNames.END_DATE, Date.class);
        end.setValue(Date.from(LocalDateTime.of(2018, 9, 1, 0, 0, 0, 0)
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                  "24.83885442747927 43.8379609098684," +
                                                  "24.83885442747927 44.795645304033826," +
                                                  "22.8042573604346 44.795645304033826," +
                                                  "22.8042573604346 43.8379609098684))");

        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.addParameter("maxClouds", 100.);
        query.setMaxResults(10);
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
    }

    static void searchNewLandsat8Test() throws Exception {
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.INFO);
        }
        DataSource dataSource = new ro.cs.tao.datasource.usgs.USGSDataSource();
        dataSource.setCredentials("kraftek", "cei7pitici.");
        String[] sensors = dataSource.getSupportedSensors();

        DataQuery query = dataSource.createQuery(sensors[0]);
        QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
        begin.setValue(Date.from(LocalDateTime.of(2018, 7, 1, 0, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault())
                                         .toInstant()));
        query.addParameter(begin);
        QueryParameter<Date> end = query.createParameter(CommonParameterNames.END_DATE, Date.class);
        end.setValue(Date.from(LocalDateTime.of(2018, 9, 1, 0, 0, 0, 0)
                                       .atZone(ZoneId.systemDefault())
                                       .toInstant()));
        query.addParameter(end);
        Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                  "24.83885442747927 43.8379609098684," +
                                                  "24.83885442747927 44.795645304033826," +
                                                  "22.8042573604346 44.795645304033826," +
                                                  "22.8042573604346 43.8379609098684))");

        query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
        query.addParameter("maxClouds", 90);
        query.setPageSize(20);
        query.setMaxResults(100);
        System.out.println(String.format("Count returned %d", query.getCount()));
        List<EOProduct> results = query.execute();
        results.forEach(r -> {
            System.out.println("ID=" + r.getId());
            System.out.println("NAME=" + r.getName());
            System.out.println("LOCATION=" + r.getLocation());
            System.out.println("FOOTPRINT=" + r.getGeometry());
        });
        final ProductFetchStrategy fetchStrategy = dataSource.getProductFetchStrategy(sensors[0]);
        ProgressListener progressListener = new ProgressListener() {
            @Override
            public void started(String taskName) {
                System.out.println("Started " + taskName);
            }

            @Override
            public void subActivityStarted(String subTaskName) {
                System.out.println("Started " + subTaskName);
            }

            @Override
            public void subActivityEnded(String subTaskName) {
                System.out.println("Completed " + subTaskName);
            }

            @Override
            public void ended() {
                System.out.println("Done");
            }

            @Override
            public void notifyProgress(double progressValue) {
                System.out.println("Progress : " + progressValue);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress) {
                System.out.println("Progress: " + subTaskName + "," + subTaskProgress);
            }

            @Override
            public void notifyProgress(String subTaskName, double subTaskProgress, double overallProgress) {
                System.out.println("Progress: " + overallProgress + " - " + subTaskName + "," + subTaskProgress);
            }
        };
        fetchStrategy.setProgressListener(progressListener);
        fetchStrategy.fetch(results.get(0));
    }

}
