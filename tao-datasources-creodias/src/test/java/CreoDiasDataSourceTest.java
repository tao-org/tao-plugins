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

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.eodata.EOData;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class CreoDiasDataSourceTest {

    private static final QueryParameter<Date> begin;
    private static final QueryParameter<Date> end;
    private static final QueryParameter<Polygon2D> aoi;
    private static final int pageSize;
    private static final int maxResults;
    private static final String rowTemplate;

    static {
        begin = new QueryParameter<>(Date.class, CommonParameterNames.START_DATE);
        begin.setValue(Date.from(LocalDateTime.of(2017, 11, 20, 0, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault())
                                         .toInstant()));
        end = new QueryParameter<>(Date.class, CommonParameterNames.END_DATE);
        end.setValue(Date.from(LocalDateTime.of(2017, 12, 31, 23, 59, 59, 0)
                                       .atZone(ZoneId.systemDefault())
                                       .toInstant()));
        Polygon2D footprint = Polygon2D.fromWKT("POLYGON((20.261024 46.114853,20.726955 46.17556,21.176666 46.295555,22.032497 47.530273,22.894804 47.95454,24.9194410000001 47.711662,26.634995 48.257164,28.119717 46.854404,28.21484 45.448647,29.664331 45.211803,29.549438 44.820267,28.868324 44.943047,28.583244 43.747765,27.036427 44.147339,25.430229 43.626778,24.179996 43.684715,22.875275 43.842499,23.044167 44.076111,22.681435 44.224701,22.457333 44.474358,22.764893 44.559006,22.479164 44.710274,22.146385 44.479164,21.400398 44.780823,21.513611 45.151108,20.261024 46.114853))");
        aoi = new QueryParameter<>(Polygon2D.class, CommonParameterNames.FOOTPRINT);
        aoi.setValue(footprint);
        pageSize = 10;
        maxResults = 20;
        rowTemplate = "ID=%s, NAME=%s, LOCATION=%s";
    }

    public static void main(String[] args) {
        //Sentinel2_Count_Test();
        Sentinel2_Test();
        //Sentinel1_Count_Test();
        //Sentinel1_Test();
        //Landsat8_Count_Test();
        //Landsat8_Test();
    }

    public static void Sentinel2_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Sentinel2 query returned %s", query.getCount()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Sentinel2_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource dataSource = new CreoDiasDataSource();
            //String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel2");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            query.setMaxResults(2000);
            List<EOProduct> results = query.execute();
            Files.write(Paths.get("W:\\creodias_rou_2017.txt"),
                        results.stream().map(EOData::getLocation).collect(Collectors.toList()));
            /*results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Sentinel1_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Sentinel1 query returned %s", query.getCount()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Sentinel1_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel1DataSource.class.getName());
            DataSource dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Sentinel1");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Landsat8_Count_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel2DataSource.class);
            DataSource dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            System.out.println(String.format("Landsat8 query returned %s", query.getCount()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Landsat8_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.FINEST);
            }
            //DataSource dataSource = getDatasourceRegistry().getService(CreoDIASSentinel1DataSource.class.getName());
            DataSource dataSource = new CreoDiasDataSource();
            String[] sensors = dataSource.getSupportedSensors();

            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter(begin);
            query.addParameter(end);
            query.addParameter(aoi);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println(String.format(rowTemplate, r.getId(), r.getName(), r.getLocation()));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }

}
