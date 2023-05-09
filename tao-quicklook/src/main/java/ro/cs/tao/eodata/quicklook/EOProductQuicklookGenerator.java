/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.eodata.quicklook;

import org.apache.commons.lang3.StringUtils;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Specialization of an output data handler to try to generate quicklooks for EOProducts.
 *
 * @author Cosmin Cara
 */
public class EOProductQuicklookGenerator extends BaseQuicklookGenerator<EOProduct> {

    public EOProductQuicklookGenerator() {
        super();
    }

    @Override
    public Class<EOProduct> isIntendedFor() { return EOProduct.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    protected List<EOProduct> handleInner(List<EOProduct> list) throws DataHandlingException {
        final OutputAccumulator consumer = new OutputAccumulator();
        for (EOProduct product : list) {
            try {
                if (DataFormat.RASTER.equals(product.getFormatType())) {
                    consumer.reset();
                    final String entryPoint = product.getEntryPoint();
                    if (StringUtils.isNotEmpty(entryPoint)) {
                        Path productPath = FileUtilities.toPath(product.getLocation()).resolve(entryPoint);
                        Executor<?> executor = initialize(productPath, DockerHelper.isDockerFound() ? getGDALCommandLineOnDocker() : getGDALCommandLineOnPath());
                        if (executor != null) {
                            executor.setOutputConsumer(consumer);
                            int code;
                            logger.fine("Trying to create quicklook for " + product.getLocation() + entryPoint);
                            if ((code = executor.execute(false)) == 0) {
                                product.setQuicklookLocation(Paths.get(productPath + extension).toString());
                            } else {
                                logger.warning(String.format("Quicklook for product %s was not created (return code: %d, output: %s)",
                                        product.getName(), code, consumer.getOutput()));
                            }
                        } else {
                            logger.warning(String.format("Automated quicklooks not supported for %s products", product.getProductType()));
                        }
                    } else {
                        logger.warning(String.format("Automated quicklooks not supported for %s products", product.getProductType()));
                    }
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for product %s. Reason: %s",
                                            product.getName(), e.getMessage()));
            }
        }
        return list;
    }

    @Override
    protected Set<String> getSupportedExtensions() {
        return new HashSet<String>() {{
            add(".tif"); add(".tiff"); add(".nc"); add(".png"); add(".jpg"); add(".bmp");
        }};
    }

    /*@Override
    protected Executor<?> initialize(Path productPath, String[] args) throws IOException {
        // At least on Windows, docker doesn't handle well folder symlinks in the path
        Path realPath = FileUtilities.resolveSymLinks(productPath);
        return super.initialize(realPath, args);
    }*/
}
