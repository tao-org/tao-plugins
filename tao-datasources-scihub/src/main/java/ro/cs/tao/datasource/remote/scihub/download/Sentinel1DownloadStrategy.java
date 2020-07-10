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
package ro.cs.tao.datasource.remote.scihub.download;

import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel1ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Cosmin Cara
 */
public class Sentinel1DownloadStrategy extends SentinelDownloadStrategy {

    public Sentinel1DownloadStrategy(SciHubDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder);
    }

    protected Sentinel1DownloadStrategy(Sentinel1DownloadStrategy other) {
        super(other);
    }

    @Override
    public Sentinel1DownloadStrategy clone() { return new Sentinel1DownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        Path archivePath = super.fetchImpl(product);
        Path productFile;
        if (Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "false"))) {
            productFile = Zipper.decompressZip(archivePath, archivePath.getParent(), true);
            if (productFile != null) {
                try {
                    if (!productFile.toString().contains(product.getName())) {
                        productFile = Paths.get(archivePath.toString().replace(".zip", ".SAFE"));
                    }
                    product.setLocation(productFile.toUri().toString());
                    ProductHelper helper = SentinelProductHelper.create(product.getName());
                    if (helper instanceof Sentinel1ProductHelper) {
                        Sentinel1ProductHelper s1Helper = (Sentinel1ProductHelper) helper;
                        s1Helper.getOrbit();
                        product.addAttribute("tiles", s1Helper.getOrbit());
                    }
                } catch (URISyntaxException e) {
                    logger.severe(e.getMessage());
                }
            }
        } else {
            productFile = archivePath;
        }
        return productFile;
    }
}
