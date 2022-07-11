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
package ro.cs.tao.datasource.remote.mundi.sentinel2;

import org.apache.http.Header;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.util.Constants;
import ro.cs.tao.datasource.util.Utilities;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.L1CProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.utils.FileUtilities;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Cosmin Cara
 */
public class Sentinel2DownloadStrategy extends DownloadStrategy<Header> {
    private static final Properties properties;
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

    private String urlProductPath;

    private Set<String> filteredTiles;
    private boolean shouldFilterTiles;
    private Pattern tileIdPattern;

    static {
        properties = new Properties();
        try {
            properties.load(MundiDataSource.class.getResourceAsStream("mundi.properties"));
        } catch (IOException ignored) {
        }
    }

    public Sentinel2DownloadStrategy(MundiDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder, properties);
        this.urlProductPath = props.getProperty("sentinel.download.url", "https://obs.eu-de.otc.t-systems.com/");
    }

    private Sentinel2DownloadStrategy(Sentinel2DownloadStrategy other) {
        super(other);
        if (other.filteredTiles != null) {
            this.filteredTiles = new HashSet<>();
            this.filteredTiles.addAll(other.filteredTiles);
        }
        this.urlProductPath = other.urlProductPath;
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
    protected boolean adjustProductLength() { return false; }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        final String location = descriptor.getLocation();
        try {
            URI.create(location);
            // if we get here, the location is a URL
            if (location.startsWith(this.urlProductPath)) {
                // the location is a MUNDI URL
                return location;
            } else {
                // the location is not a MUNDI URL, we have to compute one
                return this.urlProductPath + computeRelativeLocation(descriptor);
            }
        } catch (IllegalArgumentException ignored) {
            // the location is not a URL, it should be relative already
            return this.urlProductPath + location;
        }
    }

    private String computeRelativeLocation(EOProduct descriptor) {
        StringBuilder builder = new StringBuilder();
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(descriptor.getName());
        String sensingDate = helper.getSensingDate();
        String tileId = helper.getTileIdentifier();
        // Since around 05-2019, MUNDI split the buckets for S2 products in quarters, so we have to compute them
        LocalDateTime date = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").parse(sensingDate));
        final int year = date.getYear();
        if (year < 2019) {
            builder.append("s2-l1c/");
        } else {
            builder.append("s2-l1c-").append(year).append("-q");
            final int month = date.getMonthValue();
            builder.append(month < 4 ? "1" : month < 7 ? "2" : month < 10 ? "3" : "4").append("/");
        }
        builder.append(tileId, 0, 2).append("/")
                .append(tileId, 2, 3).append("/")
                .append(tileId, 3, 5).append("/");
        builder.append(sensingDate, 0, 4).append("/")
                .append(sensingDate, 4, 6).append("/")
                .append(sensingDate, 6, 8).append("/")
                .append(descriptor.getName());
        return builder.toString();
    }

    @Override
    protected String getMetadataUrl(EOProduct product) {
        final Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(product.getName());
        return getProductUrl(product) + "/" + helper.getMetadataFileName();
    }

    @Override
    protected java.nio.file.Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
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
    protected java.nio.file.Path link(EOProduct product) throws IOException {
        String productName = product.getName();
        String localArchiveRoot = getLocalArchiveRoot();
        if (localArchiveRoot == null) {
            throw new IllegalArgumentException("Local archive root not set");
        }
        java.nio.file.Path productRepositoryPath = Paths.get(localArchiveRoot);
        java.nio.file.Path destinationPath = FileUtilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
        java.nio.file.Path productSourcePath = findProductPath(productRepositoryPath, product);
        if (productSourcePath == null) {
            logger.warning(String.format("%s not found locally", productName));
            return null;
        }
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        java.nio.file.Path metadataFile = destinationPath.resolve(helper.getMetadataFileName());
        currentStep = "Metadata";
        logger.fine(String.format("Copying metadata file %s", metadataFile));
        FileUtilities.ensurePermissions(copyFile(productSourcePath.resolve(metadataFile.getFileName()), metadataFile));
        if (Files.exists(metadataFile)) {
            List<String> allLines = Files.readAllLines(metadataFile);
            Set<String> tileNames = updateMetadata(metadataFile, allLines);
            if (tileNames != null) {
                currentProduct.addAttribute("tiles", String.join(",", tileNames));
                FileUtilities.link(productSourcePath, destinationPath,
                                   folder -> !folder.toString().contains("GRANULE") ||
                                           "GRANULE".equals(folder.getName(folder.getNameCount() - 1).toString()) ||
                                           tileNames.stream().anyMatch(tn -> folder.toString().contains(tn)),
                                   null);
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

    private java.nio.file.Path downloadImpl(EOProduct product) throws IOException, InterruptedException {
        java.nio.file.Path rootPath = null;
        String url;
        FileUtilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        String metadataFileName = helper.getMetadataFileName();
        boolean isL1C = helper instanceof L1CProductHelper;
        product.setEntryPoint(metadataFileName);
        rootPath = FileUtilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
        url = getMetadataUrl(product);
        java.nio.file.Path metadataFile = rootPath.resolve(metadataFileName);
        currentStep = "Metadata";
        downloadFile(url, metadataFile);
        if (Files.exists(metadataFile)) {
            final Path pathBuilder = new Path(getProductUrl(product));
            List<String> allLines = Files.readAllLines(metadataFile);
            List<String> metaTileNames = Utilities.filter(allLines, "<Granule" + ("13".equals(helper.getVersion()) ? "s" : " "));
            Set<String> tileIds = updateMetadata(metadataFile, allLines);
            boolean hasTiles = false;
            if (tileIds != null) {
                hasTiles = true;
                currentProduct.addAttribute("tiles", String.join(",", tileIds));
            }
            if (hasTiles) {
                java.nio.file.Path tilesFolder = FileUtilities.ensureExists(rootPath.resolve(FOLDER_GRANULE));
                FileUtilities.ensureExists(rootPath.resolve(FOLDER_AUXDATA));
                java.nio.file.Path dataStripFolder = FileUtilities.ensureExists(rootPath.resolve(FOLDER_DATASTRIP));
                Map<String, String> tileNames = new HashMap<>();
                String dataStripId = null;
                StringBuilder skippedTiles = new StringBuilder();
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
                        pathBuilder.reset();
                        tileNames.put(granuleId,
                                      pathBuilder.node(FOLDER_GRANULE).node(granule).value());
                    } else {
                        skippedTiles.append(tileId).append(" ");
                    }
                }
                if (skippedTiles.toString().trim().length() > 0) {
                    logger.fine(String.format("Skipped tiles: %s", skippedTiles.toString()));
                }
                String count = String.valueOf(tileNames.size());
                int tileCounter = 1;
                List<String> downloadedTiles = new ArrayList<>();
                for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                    long start = System.currentTimeMillis();
                    currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                    String tileUrl = entry.getValue();
                    String granuleId = entry.getKey();
                    String tileName = helper.getGranuleFolder(dataStripId, granuleId);
                    java.nio.file.Path tileFolder = FileUtilities.ensureExists(tilesFolder.resolve(tileName));
                    java.nio.file.Path auxData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_AUXDATA));
                    java.nio.file.Path imgData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_IMG_DATA));
                    java.nio.file.Path qiData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_QI_DATA));
                    String metadataName = helper.getGranuleMetadataFileName(granuleId);
                    java.nio.file.Path tileMetaFile = downloadFile(pathBuilder.root(tileUrl).node(metadataName).value(), tileFolder.resolve(metadataName));
                    if (tileMetaFile != null) {
                        if (Files.exists(tileMetaFile)) {
                            if (isL1C) {
                                for (String bandFileName : l1cBandFiles) {
                                    downloadFile(pathBuilder.root(tileUrl)
                                                         .node(FOLDER_IMG_DATA)
                                                         .node(helper.getBandFileName(granuleId, bandFileName))
                                                         .value(),
                                                 imgData.resolve(helper.getBandFileName(granuleId, bandFileName)));
                                }
                            } else {
                                for (Map.Entry<String, Set<String>> resEntry : l2aBandFiles.entrySet()) {
                                    java.nio.file.Path imgDataRes = FileUtilities.ensureExists(imgData.resolve(resEntry.getKey()));
                                    for (String bandFileName : resEntry.getValue()) {
                                        downloadFile(pathBuilder.root(tileUrl)
                                                             .node(FOLDER_IMG_DATA)
                                                             .node(resEntry.getKey())
                                                             .node(helper.getBandFileName(granuleId, bandFileName))
                                                             .value(),
                                                     imgDataRes.resolve(helper.getBandFileName(granuleId, bandFileName)));
                                    }
                                }
                            }
                            List<String> lines = Utilities.filter(Files.readAllLines(tileMetaFile),
                                                                  "<MASK_FILENAME");
                            for (String line : lines) {
                                line = line.trim();
                                int firstTagCloseIdx = line.indexOf(">") + 1;
                                int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                                String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                                maskFileName = maskFileName.substring(maskFileName.lastIndexOf(Constants.URL_SEPARATOR) + 1);
                                downloadFile(pathBuilder.root(tileUrl)
                                                     .node(FOLDER_QI_DATA)
                                                     .node(maskFileName)
                                                     .value(),
                                             qiData.resolve(maskFileName));
                            }
                            if (!isL1C) {
                                for (String maskFileName : l2aMasks) {
                                    downloadFile(pathBuilder.root(tileUrl)
                                                         .node(FOLDER_QI_DATA)
                                                         .node(helper.getBandFileName(granuleId, maskFileName))
                                                         .value(),
                                                 qiData.resolve(helper.getBandFileName(granuleId, maskFileName)));
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
                    pathBuilder.reset();
                    String dataStripPath = pathBuilder.root(getProductUrl(product)).node(FOLDER_DATASTRIP).node(dsFolder).node(dsFileName).value();
                    java.nio.file.Path dataStrip = FileUtilities.ensureExists(dataStripFolder.resolve(dsFolder));
                    downloadFile(dataStripPath, dataStrip.resolve(dsFileName));
                }
                if (downloadedTiles.size() > 0) {
                    final Pattern tilePattern = helper.getTilePattern();
                    product.addAttribute("tiles", downloadedTiles.stream().map(t -> {
                        Matcher matcher = tilePattern.matcher(t);
                        //noinspection ResultOfMethodCallIgnored
                        matcher.matches();
                        return matcher.group(1);
                    }).collect(Collectors.joining(",")));
                }
            } else {
                // remove the entire directory
                try (Stream<java.nio.file.Path> stream = Files.walk(rootPath)) {
                    stream.sorted(Comparator.reverseOrder())
                          .map(java.nio.file.Path::toFile)
                          .peek(System.out::println)
                          .forEach(File::delete);
                }
                logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
                throw new NoSuchElementException(String.format("The product %s did not contain any tiles from the tile list", productName));
            }
        } else {
            // remove the entire directory
            try (Stream<java.nio.file.Path> stream = Files.walk(rootPath)) {
                stream.sorted(Comparator.reverseOrder())
                      .map(java.nio.file.Path::toFile)
                      .peek(System.out::println)
                      .forEach(File::delete);
            }
            logger.warning(String.format("The product %s was not found", productName));
            rootPath = null;
        }
        return rootPath;
    }

    private Set<String> updateMetadata(java.nio.file.Path metaFile, List<String> originalLines) throws IOException {
        Set<String> extractedTileNames = null;
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(currentProduct.getName());
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
            if (tileCount > 0 && !Sentinel2ProductHelper.PSD_14.equals(helper.getVersion())) {
                Files.write(metaFile, lines, StandardCharsets.UTF_8);
            }
        } else {
            if (tileIdPattern == null) {
                tileIdPattern = helper.getTilePattern();
            }
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
                        }
                        i += 16;
                    } else if (line.contains("<Granule ")) {
                        final Matcher matcher = tileIdPattern.matcher(line);
                        if (matcher.matches()) {
                            if (extractedTileNames == null) {
                                extractedTileNames = new HashSet<>();
                            }
                            extractedTileNames.add(matcher.group(1));
                        }
                        i += 15;
                    }
                }
            }
        }
        return extractedTileNames;
    }

    class Path {
        private String root;
        private StringBuilder buffer;

        Path() {
            buffer = new StringBuilder();
        }

        Path(String root) {
            this();
            this.root = root;
        }

        Path root(String path) {
            this.root = path;
            buffer.setLength(0);
            buffer.append(path);
            return this;
        }

        Path node(String nodeName) {
            buffer.append("/").append(nodeName);
            return this;
        }

        void reset() {
            buffer.setLength(0);
            buffer.append(this.root);
        }

        String value() {
            return buffer.toString();
        }
    }
}
