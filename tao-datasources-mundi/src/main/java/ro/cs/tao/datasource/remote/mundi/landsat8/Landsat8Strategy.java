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
package ro.cs.tao.datasource.remote.mundi.landsat8;

import org.apache.http.Header;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.util.Constants;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.landsat.Landsat8ProductHelper;
import ro.cs.tao.utils.FileUtilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author Cosmin Cara
 */
public class Landsat8Strategy extends DownloadStrategy<Header> {
    private static final Properties properties;
    private static final Set<String> bandFiles = new LinkedHashSet<String>() {{
        add("_B1.TIF");
        add("_B2.TIF");
        add("_B3.TIF");
        add("_B4.TIF");
        add("_B5.TIF");
        add("_B6.TIF");
        add("_B7.TIF");
        add("_B8.TIF");
        add("_B9.TIF");
        add("_B10.TIF");
        add("_B11.TIF");
        add("_BQA.TIF");
    }};

    private String baseUrl;

    static {
        properties = new Properties();
        try {
            properties.load(MundiDataSource.class.getResourceAsStream("mundi.properties"));
        } catch (IOException ignored) {
        }
    }

    public Landsat8Strategy(MundiDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder, properties);
        baseUrl = props.getProperty("landsat.download.url", "https://obs.eu-de.otc.t-systems.com/l8-l1gt/");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
    }

    private Landsat8Strategy(Landsat8Strategy other) {
        super(other);
        this.baseUrl = other.baseUrl;
    }

    @Override
    public Landsat8Strategy clone() { return new Landsat8Strategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        String url;
        checkCancelled();
        currentProduct = product;
        String productName = currentProduct.getName();
        Landsat8ProductHelper helper = new Landsat8ProductHelper(productName);
        String tileId = "";
        if (this.filteredTiles != null) {
            Matcher matcher = tileIdPattern.matcher(productName);
            if (!matcher.matches()) {
                throw new NoSuchElementException(String.format("The product %s did not contain any tiles from the tile list", product.getName()));
            }
            if (matcher.groupCount() == 1) {
                // group(0) contains whole matched string and group(1) is actually the group we want
                tileId = matcher.group(1);
            }
        }
        Path rootPath = FileUtilities.ensureExists(Paths.get(destination, productName));
        url = getMetadataUrl(currentProduct);
        Path metadataFile = rootPath.resolve(productName + "_MTL.txt");
        product.setEntryPoint(metadataFile.getFileName().toString());
        currentStep = "Metadata";
        logger.fine(String.format("Downloading metadata file %s", metadataFile));
        metadataFile = downloadFile(url, metadataFile);
        if (metadataFile != null && Files.exists(metadataFile)) {
            for (String suffix : bandFiles) {
                String bandFileName = productName + suffix;
                currentStep = "Band " + bandFileName;
                try {
                    String bandFileUrl = getProductUrl(currentProduct) + Constants.URL_SEPARATOR + bandFileName;
                    Path path = rootPath.resolve(bandFileName);
                    logger.fine(String.format("Downloading band raster %s from %s", path, bandFileUrl));
                    downloadFile(bandFileUrl, path);
                } catch (IOException ex) {
                    logger.warning(String.format("Download for %s failed [%s]", bandFileName, ex.getMessage()));
                }
            }
            if ("coll".equals(helper.getVersion())) {
                String fileName = productName + "_ANG.txt";
                try {
                    String fileUrl = getProductUrl(currentProduct) + Constants.URL_SEPARATOR + fileName;
                    Path path = rootPath.resolve(fileName);
                    logger.fine(String.format("Downloading band raster %s from %s", path, fileUrl));
                    downloadFile(fileUrl, path);
                } catch (IOException ex) {
                    logger.warning(String.format("Download for %s failed [%s]", fileName, ex.getMessage()));
                }
            }
            product.addAttribute("tiles",tileId);
        } else {
            logger.warning(
                    String.format("Either the product %s was not found or the metadata file could not be downloaded",
                                  productName));
            rootPath = null;
        }
        return rootPath;
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        return baseUrl + new Landsat8ProductHelper(descriptor.getName()).getProductRelativePath();
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        return getProductUrl(descriptor) + Constants.URL_SEPARATOR + descriptor.getName() + "_MTL.txt";
    }
}
