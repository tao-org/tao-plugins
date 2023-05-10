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
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.configuration.EmptyConfigurationProvider;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.theia.TheiaDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Cosmin Cara
 */
public class DataSourceTest {

    public static void main(String[] args) {
        setup();
        query_Test();
    }

    private static void printParams() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        DataSource<?, ?> dataSource = getDatasourceRegistry().getService(TheiaDataSource.class);
        System.out.println(mapper.writeValueAsString(dataSource.getSupportedParameters()));
    }

    private static void query_Test() {
        try {
            Logger logger = LogManager.getLogManager().getLogger("");
            for (Handler handler : logger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            DataSource<?, ?> dataSource = getDatasourceRegistry().getService(TheiaDataSource.class);
            dataSource.setCredentials("", "");
            DataQuery query = dataSource.createQuery("Landsat8");
            query.addParameter("collection", "LANDSAT");
            QueryParameter<LocalDateTime> begin = query.createParameter(CommonParameterNames.START_DATE, LocalDateTime.class);
            begin.setMinValue(LocalDateTime.of(2019, 2, 1, 0, 0, 0, 0));
            begin.setMaxValue(LocalDateTime.of(2019, 3, 1, 0, 0, 0, 0));
            query.addParameter(begin);
            Polygon2D aoi = Polygon2D.fromWKT("POLYGON((-0.0941841235145851 47.76641577395455,-0.02387001418917634 44.20164248362536,6.655817485810821 44.25202844462507,5.390190340043613 47.908004837800206,-0.0941841235145851 47.76641577395455))");

            query.addParameter(CommonParameterNames.FOOTPRINT, aoi);
            query.setPageSize(10);
            query.setMaxResults(50);
            List<EOProduct> results = query.execute();
            results.forEach(r -> {
                System.out.println("ID=" + r.getId());
                System.out.println("NAME=" + r.getName());
                System.out.println("LOCATION=" + r.getLocation());
                System.out.println("FOOTPRINT=" + r.getGeometry());
            });
            DownloadStrategy<?> strategy = (DownloadStrategy<?>) dataSource.getProductFetchStrategy("Landsat8");
            DownloadProgressListener progressListener = new DownloadProgressListener() {};
            strategy.setProgressListener(progressListener);
            strategy.setDestination(ConfigurationManager.getInstance().getValue("products.location"));
            strategy.setFetchMode(FetchMode.OVERWRITE);
            strategy.fetch(results.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setup() {
        ConfigurationProvider configurationProvider = new EmptyConfigurationProvider();
        final String userHome = System.getProperty("user.home");
        configurationProvider.setValue("products.location", userHome);
        configurationProvider.setScriptsFolder(Paths.get(userHome));
        configurationProvider.setConfigurationFolder(Paths.get(userHome));
        ConfigurationManager.setConfigurationProvider(configurationProvider);
    }

    private static ServiceRegistry<DataSource> getDatasourceRegistry() {
        return ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
    }
}
