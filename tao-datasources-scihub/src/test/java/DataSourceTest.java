/*
 *
 *  * Copyright (C) 2018 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *  *
 *
 */

/*
 *
 *  * Copyright (C) 2018 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *  *
 *
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.*;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class DataSourceTest {

    public static void main(String[] args) throws JsonProcessingException {
        //SciHub_Sentinel1_Test();
        //SciHub_Sentinel2_Count_Test();
        //SciHub_Sentinel2_Test();
        printParams();
    }

    private static void printParams() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        DataSource dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
        System.out.println(mapper.writeValueAsString(dataSource.getSupportedParameters()));
    }

    private static void SciHub_Sentinel2_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            DataSource dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("kraftek", "cei7samurai");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[1]);
            query.addParameter(CommonParameterNames.PLATFORM, "Sentinel-2");
            QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
            begin.setMinValue(Date.from(LocalDateTime.of(2016, 2, 1, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            begin.setMaxValue(Date.from(LocalDateTime.of(2017, 2, 1, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            query.addParameter(begin);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                      "24.83885442747927 43.8379609098684," +
                                                      "24.83885442747927 44.795645304033826," +
                                                      "22.8042573604346 44.795645304033826," +
                                                      "22.8042573604346 43.8379609098684))");

            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);

            query.addParameter(CommonParameterNames.CLOUD_COVER, 100.);
            System.out.println(String.format("Query returned %s",query.getCount()));
        } catch (QueryException e) {
            e.printStackTrace();
        }
    }

    private static void SciHub_Sentinel2_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            DataSource dataSource = getDatasourceRegistry().getService(SciHubDataSource.class);
            dataSource.setCredentials("kraftek", "cei7samurai");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[1]);
            query.addParameter(CommonParameterNames.PLATFORM, "Sentinel-2");
            QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
            begin.setMinValue(Date.from(LocalDateTime.of(2019, 2, 1, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            begin.setMaxValue(Date.from(LocalDateTime.of(2019, 3, 1, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            query.addParameter(begin);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                      "24.83885442747927 43.8379609098684," +
                                                      "24.83885442747927 44.795645304033826," +
                                                      "22.8042573604346 44.795645304033826," +
                                                      "22.8042573604346 43.8379609098684))");

            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);

            query.addParameter(CommonParameterNames.CLOUD_COVER, 100.);
            query.setPageSize(50);
            query.setMaxResults(83);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes()
                        .forEach(a -> System.out.println("\tName='" + a.getName() +
                                                                 "', value='" + a.getValue() + "'"));
            });
            DownloadStrategy strategy = (DownloadStrategy) dataSource.getProductFetchStrategy(sensors[0]);
            strategy.setFetchMode(FetchMode.OVERWRITE);
            strategy.fetch(results.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void SciHub_Sentinel1_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            DataSource dataSource = getDatasourceRegistry().getService(SciHubDataSource.class.getName());
            //new SciHubDataSource();
            dataSource.setCredentials("kraftek", "cei7samurai");
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery(sensors[0]);
            query.addParameter(CommonParameterNames.PLATFORM, "Sentinel-1");
            QueryParameter<Date> begin = query.createParameter(CommonParameterNames.START_DATE, Date.class);
            begin.setMinValue(Date.from(LocalDateTime.of(2018, 11, 1, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            begin.setMaxValue(Date.from(LocalDateTime.of(2018, 12, 15, 0, 0, 0, 0)
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()));
            query.addParameter(begin);
            query.addParameter(CommonParameterNames.POLARISATION, "VV");
            query.addParameter("sensorOperationalMode", "IW");
            query.addParameter(CommonParameterNames.PRODUCT_TYPE, "SLC");
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((22.8042573604346 43.8379609098684," +
                                                      "24.83885442747927 43.8379609098684," +
                                                      "24.83885442747927 44.795645304033826," +
                                                      "22.8042573604346 44.795645304033826," +
                                                      "22.8042573604346 43.8379609098684))");

            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
            query.setPageSize(10);
            query.setMaxResults(10);
            //SentinelDownloadStrategy downloader = new SentinelDownloadStrategy("E:\\NewFormat");
            List<EOProduct> results = query.execute();
            //downloader.download(results);
            Path repositoryPath = Paths.get("/mnt/products");
            String localPathFormat = ".";
            Properties properties = new Properties();
            properties.put(ProductPathBuilder.LOCAL_ARCHIVE_PATH_FORMAT, ".");
            properties.put(ProductPathBuilder.PATH_SUFFIX, "none");
            properties.put(ProductPathBuilder.PRODUCT_FORMAT, "zip");
            ProductPathBuilder pathBuilder = new DefaultProductPathBuilder(repositoryPath, localPathFormat, properties, true);
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
                System.out.println("Attributes ->");
                r.getAttributes()
                  .forEach(a -> System.out.println("\tName='" + a.getName() +
                    "', value='" + a.getValue() + "'"));
                Path path = pathBuilder.getProductPath(repositoryPath, r);
                if (!path.toString().contains(r.getName()) &&
                        (!path.toString().toLowerCase().endsWith(".zip") || !path.toString().toLowerCase().endsWith(".tar.gz"))) {
                    path = path.resolve(r.getName());
                }
                System.out.println(path.toUri().toString());
            });
        } catch (QueryException e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
