/*
 * Copyright (C) 2017 CS ROMANIA
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
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.datasource.util.Utilities;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.utils.FileUtils;
import ro.cs.tao.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class Sentinel2DownloadStrategy extends SentinelDownloadStrategy {
    private static final Set<String> l1cBandFiles = new LinkedHashSet<String>() {{
        add("B01.jp2");
        add("B02.jp2");
        add("B03.jp2");
        add("B04.jp2");
        add("B05.jp2");
        add("B06.jp2");
        add("B07.jp2");
        add("B08.jp2");
        add("B8A.jp2");
        add("B09.jp2");
        add("B10.jp2");
        add("B11.jp2");
        add("B12.jp2");
    }};
    private static final Map<String, Set<String>> l2aBandFiles = new HashMap<String, Set<String>>() {{
        Set<String> files = new LinkedHashSet<>();
        put("R10m", files);
        files.add("AOT_10m.jp2");
        files.add("B02_10m.jp2");
        files.add("B03_10m.jp2");
        files.add("B04_10m.jp2");
        files.add("B08_10m.jp2");
        files.add("TCI_10m.jp2");
        files.add("WVP_10m.jp2");
        files = new LinkedHashSet<>();
        put("R20m", files);
        files.add("AOT_20m.jp2");
        files.add("B02_20m.jp2");
        files.add("B03_20m.jp2");
        files.add("B04_20m.jp2");
        files.add("B05_20m.jp2");
        files.add("B06_20m.jp2");
        files.add("B07_20m.jp2");
        files.add("B11_20m.jp2");
        files.add("B12_20m.jp2");
        files.add("B8A_20m.jp2");
        files.add("SCL_20m.jp2");
        files.add("TCI_20m.jp2");
        files.add("VIS_20m.jp2");
        files.add("WVP_20m.jp2");
        files = new LinkedHashSet<>();
        put("R60m", files);
        files.add("AOT_60m.jp2");
        files.add("B01_60m.jp2");
        files.add("B02_60m.jp2");
        files.add("B03_60m.jp2");
        files.add("B04_60m.jp2");
        files.add("B05_60m.jp2");
        files.add("B06_60m.jp2");
        files.add("B07_60m.jp2");
        files.add("B09_60m.jp2");
        files.add("B11_60m.jp2");
        files.add("B12_60m.jp2");
        files.add("B8A_60m.jp2");
        files.add("SCL_60m.jp2");
        files.add("TCI_60m.jp2");
        files.add("WVP_60m.jp2");
    }};
    private static final Set<String> l2aMasks = new LinkedHashSet<String>() {{
        add("CLD_20m.jp2");
        add("CLD_60m.jp2");
        add("PVI.jp2");
        add("SNW_20m.jp2");
        add("SNW_60m.jp2");
    }};

    private static final String FOLDER_GRANULE = "GRANULE";
    private static final String FOLDER_AUXDATA = "AUX_DATA";
    private static final String FOLDER_DATASTRIP = "DATASTRIP";
    private static final String FOLDER_IMG_DATA = "IMG_DATA";
    private static final String FOLDER_QI_DATA = "QI_DATA";
    private static final String XML_ATTR_GRANULE_ID = "granuleIdentifier";
    private static final String XML_ATTR_DATASTRIP_ID = "datastripIdentifier";

    private String odataProductPath;
    private String odataTilePath;
    private String odataMetadataPath;

    private Set<String> filteredTiles;
    private boolean shouldFilterTiles;
    private Pattern tileIdPattern;

    public Sentinel2DownloadStrategy(String targetFolder) {
        super(targetFolder);
        ODataPath odp = new ODataPath();
        odataProductPath = odp.root(oDataBasePath).node("${PRODUCT_NAME}.SAFE").path();
        odp.root(odataProductPath).node(FOLDER_GRANULE).node("${tile}");
        odataTilePath = odp.path();
        odataMetadataPath = odp.root(odataProductPath).node(ODATA_XML_PLACEHOLDER).value();
    }

    private Sentinel2DownloadStrategy(Sentinel2DownloadStrategy other) {
        super(other);
        if (other.filteredTiles != null) {
            this.filteredTiles = new HashSet<>();
            this.filteredTiles.addAll(other.filteredTiles);
        }
        this.odataMetadataPath = other.odataMetadataPath;
        this.odataProductPath = other.odataProductPath;
        this.odataTilePath = other.odataTilePath;
        this.shouldFilterTiles = other.shouldFilterTiles;
        this.tileIdPattern = other.tileIdPattern;
    }

    @Override
    public Sentinel2DownloadStrategy clone() { return new Sentinel2DownloadStrategy(this); }

    public void setFilteredTiles(Set<String> tiles) {
        this.filteredTiles = tiles;
        if (shouldFilterTiles = (tiles != null && tiles.size() > 0)) {
            StringBuilder text = new StringBuilder();
            text.append("(?:.+)(");
            int idx = 1, n = tiles.size();
            for (String tile : tiles) {
                text.append(tile);
                if (idx++ < n)
                    text.append("|");
            }
            text.append(")(?:.+)");
            tileIdPattern = Pattern.compile(text.toString());
        }
    }

    @Override
    protected String getMetadataUrl(EOProduct product) {
        final Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(product.getName());
        String metadataFile = helper.getMetadataFileName();
        return odataMetadataPath.replace(ODATA_UUID, product.getId())
                .replace(ODATA_PRODUCT_NAME, product.getName())
                .replace(ODATA_XML_PLACEHOLDER, metadataFile);
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(product.getName());
        String tileId = helper.getTileIdentifier();
        if (tileId != null && this.filteredTiles != null && !this.filteredTiles.contains(tileId)) {
            logger.warning(String.format("The product %s did not contain any tiles from the tile list", product.getName()));
            return null;
        } else {
            return downloadImpl(product);
        }
    }

    @Override
    protected Path link(EOProduct product) throws IOException {
        String productName = product.getName();
        String localArchiveRoot = getLocalArchiveRoot();
        if (localArchiveRoot == null) {
            throw new IllegalArgumentException("Local archive root not set");
        }
        Path productRepositoryPath = Paths.get(localArchiveRoot);
        Path destinationPath = FileUtils.ensureExists(Paths.get(destination, productName + ".SAFE"));
        Path productSourcePath = findProductPath(productRepositoryPath, product);
        if (productSourcePath == null) {
            logger.warning(String.format("%s not found locally", productName));
            return null;
        }
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        Path metadataFile = destinationPath.resolve(helper.getMetadataFileName());
        currentStep = "Metadata";
        logger.fine(String.format("Copying metadata file %s", metadataFile));
        copyFile(productSourcePath.resolve(metadataFile.getFileName()), metadataFile);
        if (Files.exists(metadataFile)) {
            List<String> allLines = Files.readAllLines(metadataFile);
            Set<String> tileNames = updateMetadata(metadataFile, allLines);
            if (tileNames != null) {
                currentProduct.addAttribute("tiles", StringUtils.join(tileNames, ","));
                List<Path> folders = FileUtils.listFolders(productSourcePath);
                final Path destPath = destinationPath;
                folders.stream()
                        .filter(folder -> !folder.toString().contains("GRANULE") ||
                                "GRANULE".equals(folder.getName(folder.getNameCount() - 1).toString()) ||
                                tileNames.stream().anyMatch(tn -> folder.toString().contains(tn)))
                        .forEach(folder -> {
                            try {
                                FileUtils.ensureExists(destPath.resolve(productSourcePath.relativize(folder)));
                                FileUtils.listFiles(folder)
                                        .forEach(file -> {
                                            try {
                                                linkFile(file, destPath.resolve(productSourcePath.relativize(file)));
                                            } catch (IOException e) {
                                                logger.warning(e.getMessage());
                                            }
                                        });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } else {
                Files.deleteIfExists(metadataFile);
                logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
                destinationPath = null;
            }
        } else {
            logger.warning(String.format("Either the product %s was not found or the metadata file could not be downloaded",
                    productName));
            destinationPath = null;
        }
        return destinationPath;
    }

    private Path downloadImpl(EOProduct product) throws IOException, InterruptedException {
        Path rootPath = null;
        String url;
        FileUtils.ensureExists(Paths.get(destination));
        String productName = product.getName();
        boolean isL1C = "Level-1C".equals(product.getAttributeValue("processinglevel"));
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        if ("13".equals(helper.getVersion())) {
            currentStep = "Archive";
            url = odataArchivePath.replace(ODATA_UUID, product.getId());
            rootPath = Paths.get(destination, productName + ".zip");
            rootPath = downloadFile(url, rootPath, NetUtils.getAuthToken());
        }
        if (rootPath == null || !Files.exists(rootPath)) {
            rootPath = FileUtils.ensureExists(Paths.get(destination, productName + ".SAFE"));
            url = getMetadataUrl(product);
            Path metadataFile = rootPath.resolve(helper.getMetadataFileName());
            currentStep = "Metadata";
            downloadFile(url, metadataFile, NetUtils.getAuthToken());
            if (Files.exists(metadataFile)) {
                List<String> allLines = Files.readAllLines(metadataFile);
                List<String> metaTileNames = Utilities.filter(allLines, "<Granule" + ("13".equals(helper.getVersion()) ? "s" : " "));
                Set<String> tileIds = updateMetadata(metadataFile, allLines);
                boolean hasTiles = false;
                if (tileIds != null) {
                    hasTiles = true;
                    currentProduct.addAttribute("tiles", StringUtils.join(tileIds, ","));
                }
                if (hasTiles) {
                    Path tilesFolder = FileUtils.ensureExists(rootPath.resolve(FOLDER_GRANULE));
                    FileUtils.ensureExists(rootPath.resolve(FOLDER_AUXDATA));
                    Path dataStripFolder = FileUtils.ensureExists(rootPath.resolve(FOLDER_DATASTRIP));
                    Map<String, String> tileNames = new HashMap<>();
                    String dataStripId = null;
                    String skippedTiles = "";
                    for (String tileName : metaTileNames) {
                        String tileId;
                        if (isL1C) {
                            tileId = tileName.substring(0, tileName.lastIndexOf(NAME_SEPARATOR));
                            tileId = tileId.substring(tileId.lastIndexOf(NAME_SEPARATOR) + 2);
                        } else {
                            int idx = tileName.lastIndexOf(NAME_SEPARATOR + "T");
                            tileId = tileName.substring(idx + 2, idx + 7);
                        }
                        if (filteredTiles == null || filteredTiles.size() == 0 || filteredTiles.contains(tileId)) {
                            String granuleId = Utilities.getAttributeValue(tileName, XML_ATTR_GRANULE_ID);
                            if (dataStripId == null) {
                                dataStripId = Utilities.getAttributeValue(tileName, XML_ATTR_DATASTRIP_ID);
                            }
                            String granule = helper.getGranuleFolder(dataStripId, granuleId);
                            tileNames.put(granuleId, odataTilePath.replace(ODATA_UUID, product.getId())
                                    .replace(ODATA_PRODUCT_NAME, productName)
                                    .replace("${tile}", granule));
                        } else {
                            skippedTiles += tileId + " ";
                        }
                    }
                    if (skippedTiles.trim().length() > 0) {
                        logger.fine(String.format("Skipped tiles: %s", skippedTiles));
                    }
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    ODataPath pathBuilder = new ODataPath();
                    List<String> downloadedTiles = new ArrayList<>();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        long start = System.currentTimeMillis();
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        String tileUrl = entry.getValue();
                        String granuleId = entry.getKey();
                        String tileName = helper.getGranuleFolder(dataStripId, granuleId);
                        Path tileFolder = FileUtils.ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = FileUtils.ensureExists(tileFolder.resolve(FOLDER_AUXDATA));
                        Path imgData = FileUtils.ensureExists(tileFolder.resolve(FOLDER_IMG_DATA));
                        Path qiData = FileUtils.ensureExists(tileFolder.resolve(FOLDER_QI_DATA));
                        String metadataName = helper.getGranuleMetadataFileName(granuleId);
                        Path tileMetaFile = downloadFile(pathBuilder.root(tileUrl).node(metadataName).value(), tileFolder.resolve(metadataName), NetUtils.getAuthToken());
                        if (tileMetaFile != null) {
                            if (Files.exists(tileMetaFile)) {
                                if (isL1C) {
                                    for (String bandFileName : l1cBandFiles) {
                                        downloadFile(pathBuilder.root(tileUrl)
                                                             .node(FOLDER_IMG_DATA)
                                                             .node(helper.getBandFileName(granuleId, bandFileName))
                                                             .value(),
                                                     imgData.resolve(helper.getBandFileName(granuleId, bandFileName)),
                                                     NetUtils.getAuthToken());
                                    }
                                } else {
                                    for (Map.Entry<String, Set<String>> resEntry : l2aBandFiles.entrySet()) {
                                        Path imgDataRes = FileUtils.ensureExists(imgData.resolve(resEntry.getKey()));
                                        for (String bandFileName : resEntry.getValue()) {
                                            downloadFile(pathBuilder.root(tileUrl)
                                                                 .node(FOLDER_IMG_DATA)
                                                                 .node(resEntry.getKey())
                                                                 .node(helper.getBandFileName(granuleId, bandFileName))
                                                                 .value(),
                                                         imgDataRes.resolve(helper.getBandFileName(granuleId, bandFileName)),
                                                         NetUtils.getAuthToken());
                                        }
                                    }
                                }
                                List<String> lines = Utilities.filter(Files.readAllLines(metadataFile),
                                                                      "<MASK_FILENAME");
                                for (String line : lines) {
                                    line = line.trim();
                                    int firstTagCloseIdx = line.indexOf(">") + 1;
                                    int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                    String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                    maskFileName = maskFileName.substring(maskFileName.lastIndexOf(URL_SEPARATOR) + 1);
                                    downloadFile(pathBuilder.root(tileUrl)
                                                         .node(FOLDER_QI_DATA)
                                                         .node(maskFileName)
                                                         .value(),
                                                 qiData.resolve(maskFileName),
                                                 NetUtils.getAuthToken());
                                }
                                if (!isL1C) {
                                    for (String maskFileName : l2aMasks) {
                                        downloadFile(pathBuilder.root(tileUrl)
                                                             .node(FOLDER_QI_DATA)
                                                             .node(helper.getBandFileName(granuleId, maskFileName))
                                                             .value(),
                                                     qiData.resolve(helper.getBandFileName(granuleId, maskFileName)),
                                                     NetUtils.getAuthToken());
                                    }
                                }
                                logger.fine(String.format("Tile download completed in %s", Utilities.formatTime(System.currentTimeMillis() - start)));
                                downloadedTiles.add(granuleId);
                            } else {
                                logger.warning(String.format("File %s was not downloaded", tileMetaFile.getFileName()));
                            }
                        }
                    }
                    if (dataStripId != null) {
                        String dsFolder = helper.getDatastripFolder(dataStripId);
                        String dsFileName = helper.getDatastripMetadataFileName(dataStripId);
                        String dataStripPath = pathBuilder.root(odataProductPath.replace(ODATA_UUID, product.getId())
                                                                        .replace(ODATA_PRODUCT_NAME, productName))
                                .node(FOLDER_DATASTRIP).node(dsFolder)
                                .node(dsFileName)
                                .value();
                        Path dataStrip = FileUtils.ensureExists(dataStripFolder.resolve(dsFolder));
                        downloadFile(dataStripPath, dataStrip.resolve(dsFileName), NetUtils.getAuthToken());
                    }
                    if (downloadedTiles.size() > 0) {
                        product.addAttribute("tiles", StringUtils.join(downloadedTiles, ","));
                    }
                } else {
                    //Files.deleteIfExists(metadataFile);
                    // remove the entire directory
                    Files.walk(rootPath)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .peek(System.out::println)
                            .forEach(File::delete);
                    rootPath = null;
                    logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
                }
            } else {
                // remove the entire directory
                Files.walk(rootPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .peek(System.out::println)
                        .forEach(File::delete);
                logger.warning(String.format("The product %s was not found", productName));
                rootPath = null;
            }
        }
        return rootPath;
    }

    private Set<String> updateMetadata(Path metaFile, List<String> originalLines) throws IOException {
        Set<String> extractedTileNames = null;
        if (shouldFilterTiles) {
            int tileCount = 0;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < originalLines.size(); i++) {
                String line = originalLines.get(i);
                boolean canProceed = line.contains("<Granule_List>") | line.contains("<Granule");
                if (canProceed) {
                    final String nextLine = originalLines.get(i + 1);
                    if (nextLine.contains("<Granules")) {
                        final Matcher matcher = tileIdPattern.matcher(nextLine);
                        if (matcher.matches()) {
                            if (extractedTileNames == null) {
                                extractedTileNames = new HashSet<>();
                            }
                            extractedTileNames.add(matcher.group(1));
                            lines.addAll(originalLines.subList(i, i + 17));
                            tileCount++;
                        }
                        i += 16;
                    } else if (line.contains("<Granule ")) {
                        final Matcher matcher = tileIdPattern.matcher(line);
                        if (matcher.matches()) {
                            if (extractedTileNames == null) {
                                extractedTileNames = new HashSet<>();
                            }
                            extractedTileNames.add(matcher.group(1));
                            lines.addAll(originalLines.subList(i, i + 16));
                            tileCount++;
                        }
                        i += 15;
                    } else {
                        lines.add(line);
                    }
                } else {
                    lines.add(line);
                }
            }
            if (tileCount > 0) {
                Files.write(metaFile, lines, StandardCharsets.UTF_8);
            }
        }
        return extractedTileNames;
    }
}
