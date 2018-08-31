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

import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.utils.FileUtils;
import ro.cs.tao.utils.executors.DebugOutputConsumer;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.ProcessExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Specialization of an output data handler to try to generate quicklooks for EOProducts.
 *
 * @author Cosmin Cara
 */
public class QuicklookGenerator implements OutputDataHandler<EOProduct> {
    private static final Set<String> extensions = new HashSet<String>() {{
        add(".tif"); add(".tiff"); add(".nc"); add(".png"); add(".jpg"); add(".bmp");
    }};
    private final Logger logger = Logger.getLogger(getClass().getName());

    @Override
    public Class<EOProduct> isIntendedFor() { return EOProduct.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public List<EOProduct> handle(List<EOProduct> list) throws DataHandlingException {
        DebugOutputConsumer consumer = new DebugOutputConsumer();
        for (EOProduct product : list) {
            try {
                Path productPath = Paths.get(URI.create(product.getLocation())).resolve(product.getEntryPoint());
                Executor executor = initialize(productPath);
                if (executor != null) {
                    executor.setOutputConsumer(consumer);
                    executor.execute(false);
                } else {
                    logger.warning(String.format("Automated quicklooks not supported for %s products", product.getProductType()));
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for product %s. Reason: %s",
                                            product.getName(), e.getMessage()));
            }
        }
        return list;
    }

    private Executor initialize(Path productPath) throws IOException {
        String extension = FileUtils.getExtension(productPath.toFile());
        if (!extensions.contains(extension.toLowerCase())) {
            return null;
        }
        List<String> args = new ArrayList<>();
        args.add("gdal_translate");
        args.add("-of");
        args.add("PNG");
        args.add("-ot");
        args.add("Byte");
        args.add("-scale");
        args.add("-outsize");
        args.add("10%");
        args.add("10%");
        args.add("-b");
        args.add("1");
        args.add(productPath.toAbsolutePath().toString());
        Path quicklookPath = Paths.get(productPath.toString() + ".png");
        args.add(quicklookPath.toString());
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                          InetAddress.getLocalHost().getHostName(),
                                          args);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
