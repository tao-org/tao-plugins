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
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
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
    private static final String[] gdalOnPathCmd = new String[] {
            "gdal_translate", "-of", "PNG", "-ot", "Byte", "-scale", "-outsize", "10%", "10%", "-b", "1", "$FULL_PATH", "$FULL_PATH.png"
    };
    private static final String[] gdalOnDocker = new String[] {
            "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt", "geodata/gdal",
            "gdal_translate", "-of", "PNG", "-ot", "Byte", "-scale", "-outsize", "10%", "10%", "-b", "1", "/mnt/$FILE", "/mnt/$FILE.png"
    };
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
                Executor executor = initialize(productPath, DockerHelper.isDockerFound() ? gdalOnDocker : gdalOnPathCmd);
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

    private Executor initialize(Path productPath, String[] args) throws IOException {
        String extension = FileUtilities.getExtension(productPath);
        if (!extensions.contains(extension.toLowerCase())) {
            return null;
        }
        List<String> arguments = new ArrayList<>();
        for (String arg : args) {
            arguments.add(arg.replace("$FULL_PATH", productPath.toString())
                              .replace("$FOLDER", productPath.getParent().toString())
                              .replace("$FILE", productPath.getFileName().toString()));
        }
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                          InetAddress.getLocalHost().getHostName(),
                                          arguments);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
