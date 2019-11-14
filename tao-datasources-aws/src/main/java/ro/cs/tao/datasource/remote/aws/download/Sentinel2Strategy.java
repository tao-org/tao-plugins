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
package ro.cs.tao.datasource.remote.aws.download;

import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.aws.AWSDataSource;
import ro.cs.tao.datasource.util.Utilities;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class Sentinel2Strategy extends AWSStrategy {
    private static final Properties properties;
    private static final Set<String> l1cBandFiles;
    private static final String FOLDER_GRANULE = "GRANULE";
    private static final String FOLDER_AUXDATA = "AUX_DATA";
    private static final String FOLDER_DATASTRIP = "DATASTRIP";
    private static final String FOLDER_IMG_DATA = "IMG_DATA";
    private static final String FOLDER_QI_DATA = "QI_DATA";

    private String productsUrl;
    private String baseUrl;

    private boolean shouldFilterTiles;

    static {
        properties = new Properties();
        try {
            properties.load(AWSDataSource.class.getResourceAsStream("aws.properties"));
        } catch (IOException ignored) {
        }
        l1cBandFiles = new LinkedHashSet<String>() {{
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
    }

    public Sentinel2Strategy(AWSDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder, properties);
        baseUrl = props.getProperty("s2.aws.tiles.url", "http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com");
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        productsUrl = baseUrl + "products/";
    }

    private Sentinel2Strategy(Sentinel2Strategy other) {
        super(other);
        this.productsUrl = other.productsUrl;
        this.baseUrl = other.baseUrl;
        this.shouldFilterTiles = other.shouldFilterTiles;
    }

    @Override
    public Sentinel2Strategy clone() { return new Sentinel2Strategy(this); }

    @Override
    public void setFilteredTiles(Set<String> tiles) {
        super.setFilteredTiles(tiles);
        shouldFilterTiles = this.filteredTiles != null && this.filteredTiles.size() > 0;
    }

    @Override
    protected String getMetadataUrl(EOProduct product) {
        return getProductUrl(product) + "metadata.xml";
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        String url = super.getProductUrl(descriptor);
        if (url == null || !url.startsWith(baseUrl)) {
            url = productsUrl + Sentinel2ProductHelper.createHelper(descriptor.getName()).getProductRelativePath();
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        Path rootPath = null;
        String url;
        checkCancelled();
        currentProduct = product;
        currentProductProgress = new ProductProgress(0, false);
        FileUtilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        // let's try to assemble the product
        rootPath = FileUtilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
        String baseProductUrl = getProductUrl(product);
        url = getMetadataUrl(product);
        String version = helper.getVersion();
        String metadataFileName = helper.getMetadataFileName();
        product.setEntryPoint(metadataFileName);
        Path metadataFile = rootPath.resolve(metadataFileName);
        currentStep = "Metadata";
        logger.fine(String.format("Downloading metadata file %s", metadataFile));
        metadataFile = downloadFile(url, metadataFile);
        if (metadataFile != null && Files.exists(metadataFile)) {
            Path inspireFile = metadataFile.resolveSibling("INSPIRE.xml");
            Path manifestFile = metadataFile.resolveSibling("manifest.safe");
            //Path previewFile = metadataFile.resolveSibling("preview.png");
            List<String> allLines = Files.readAllLines(metadataFile);
            List<String> metaTileNames = Utilities.filter(allLines, "<Granule |<Granules ");

            Set<String> tileIds = updateMetadata(metadataFile, allLines);
            if (tileIds != null) {
                currentProduct.addAttribute("tiles", String.join(",", tileIds));
                downloadFile(baseProductUrl + "inspire.xml", inspireFile);
                downloadFile(baseProductUrl + "manifest.safe", manifestFile);
                //downloadFile(baseProductUrl + "preview.png", previewFile);

                // rep_info folder and contents
                Path repFolder = FileUtilities.ensureExists(rootPath.resolve("rep_info"));
                Path schemaFile = repFolder.resolve("S2_User_Product_Level-1C_Metadata.xsd");
                copyFromResources(String.format("S2_User_Product_Level-1C_Metadata%s.xsd", version), schemaFile);
                // HTML folder and contents
                Path htmlFolder = FileUtilities.ensureExists(rootPath.resolve("HTML"));
                copyFromResources("banner_1.png", htmlFolder);
                copyFromResources("banner_2.png", htmlFolder);
                copyFromResources("banner_3.png", htmlFolder);
                copyFromResources("star_bg.jpg", htmlFolder);
                copyFromResources("UserProduct_index.html", htmlFolder);
                copyFromResources("UserProduct_index.xsl", htmlFolder);

                Path tilesFolder = FileUtilities.ensureExists(rootPath.resolve(FOLDER_GRANULE));
                FileUtilities.ensureExists(rootPath.resolve(FOLDER_AUXDATA));
                Path dataStripFolder = FileUtilities.ensureExists(rootPath.resolve(FOLDER_DATASTRIP));
                String productJsonUrl = baseProductUrl + "productInfo.json";
                HttpURLConnection connection = null;
                InputStream inputStream = null;
                JsonReader reader = null;
                try {
                    logger.fine(String.format("Downloading json product descriptor %s", productJsonUrl));
                    connection = ((AWSDataSource) this.dataSource).buildS3Connection(HttpMethod.GET, productJsonUrl);
                    inputStream = connection.getInputStream();
                    reader = Json.createReader(inputStream);
                    logger.fine(String.format("Parsing json descriptor %s", productJsonUrl));
                    JsonObject obj = reader.readObject();
                    final Map<String, String> tileNames = getTileNames(obj, baseUrl, metaTileNames, version, tileIds);
                    String dataStripId = null;
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    List<String> downloadedTiles = new ArrayList<>();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        String tileUrl = entry.getValue();
                        String tileName = entry.getKey();
                        Path tileFolder = FileUtilities.ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_AUXDATA));
                        Path imgData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_IMG_DATA));
                        Path qiData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_QI_DATA));
                        String metadataName = helper.getGranuleMetadataFileName(tileName);
                        Path tileMetadataPath = tileFolder.resolve(metadataName);
                        logger.fine(String.format("Downloading tile metadata %s", tileMetadataPath));
                        downloadFile(tileUrl + "/metadata.xml", tileMetadataPath);
                        for (String bandFileName : l1cBandFiles) {
                            try {
                                String bandFileUrl = tileUrl + URL_SEPARATOR + bandFileName;
                                Path path = imgData.resolve(helper.getBandFileName(tileName, bandFileName));
                                logger.fine(String.format("Downloading band raster %s from %s", path, bandFileName));
                                downloadFile(bandFileUrl, path);
                            } catch (IOException ex) {
                                logger.warning(String.format("Download for %s failed [%s]", bandFileName, ex.getMessage()));
                            }
                        }
                        List<String> tileMetadataLines = Files.readAllLines(tileMetadataPath);
                        List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                        for (String line : lines) {
                            line = line.trim();
                            int firstTagCloseIdx = line.indexOf(">") + 1;
                            int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                            String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                            String remoteName;
                            Path path;
                            if ("13".equals(version)) {
                                String[] tokens = maskFileName.split(NAME_SEPARATOR);
                                remoteName = tokens[2] + NAME_SEPARATOR + tokens[3] + NAME_SEPARATOR + tokens[9] + ".gml";
                                path = qiData.resolve(maskFileName);
                            } else {
                                remoteName = maskFileName.substring(maskFileName.lastIndexOf(URL_SEPARATOR) + 1);
                                path = rootPath.resolve(maskFileName);
                            }

                            try {
                                String fileUrl = tileUrl + "/qi/" + remoteName;
                                logger.fine(String.format("Downloading file %s from %s", path, fileUrl));
                                downloadFile(fileUrl, path);
                            } catch (IOException ex) {
                                logger.warning(String.format("Download for %s failed [%s]", path, ex.getMessage()));
                            }
                        }
                        downloadedTiles.add(tileName);
                        logger.fine(String.format("Trying to download %s", tileUrl + "/auxiliary/ECMWFT"));
                        downloadFile(tileUrl + "/auxiliary/ECMWFT", auxData.resolve(helper.getEcmWftFileName(tileName)));
                        if (dataStripId == null) {
                            String tileJson = tileUrl + "/tileInfo.json";
                            HttpURLConnection tileConnection = null;
                            InputStream is = null;
                            JsonReader tiReader = null;
                            try {
                                logger.fine(String.format("Downloading json tile descriptor %s", tileJson));
                                tileConnection = ((AWSDataSource) this.dataSource).buildS3Connection(HttpMethod.GET, tileJson);
                                is = tileConnection.getInputStream();
                                tiReader = Json.createReader(is);
                                logger.fine(String.format("Parsing json tile descriptor %s", tileJson));
                                JsonObject tileObj = tiReader.readObject();
                                dataStripId = tileObj.getJsonObject("datastrip").getString("id");
                                String dataStripPath = tileObj.getJsonObject("datastrip").getString("path") + "/metadata.xml";
                                Path dataStrip = FileUtilities.ensureExists(dataStripFolder.resolve(helper.getDatastripFolder(dataStripId)));
                                String dataStripFile = helper.getDatastripMetadataFileName(dataStripId);
                                FileUtilities.ensureExists(dataStrip.resolve(FOLDER_QI_DATA));
                                logger.fine(String.format("Downloading %s", baseUrl + dataStripPath));
                                downloadFile(baseUrl + dataStripPath, dataStrip.resolve(dataStripFile));
                            } finally {
                                if (tiReader != null) tiReader.close();
                                if (is != null) is.close();
                                if (tileConnection != null) tileConnection.disconnect();
                            }
                        }
                    }
                    if (downloadedTiles.size() > 0) {
                        final Pattern tilePattern = helper.getTilePattern();
                        product.addAttribute("tiles", String.join(",", downloadedTiles.stream().map(t -> {
                            Matcher matcher = tilePattern.matcher(t);
                            //noinspection ResultOfMethodCallIgnored
                            matcher.matches();
                            return matcher.group(1);
                        }).collect(Collectors.toList())));
                    }
                } finally {
                    if (reader != null) reader.close();
                    if (inputStream != null) inputStream.close();
                    if (connection != null) connection.disconnect();
                }
            } else {
                // remove the entire directory
                FileUtilities.deleteTree(rootPath);
                rootPath = null;
                logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
                throw new NoSuchElementException(String.format("The product %s did not contain any tiles from the tile list", productName));
            }
        } else {
            // remove the entire directory
            FileUtilities.deleteTree(rootPath);
            logger.warning(String.format("Either the product %s was not found in the data bucket or the metadata file could not be downloaded", productName));
            rootPath = null;
        }
        return rootPath;
    }

    @Override
    protected Path link(EOProduct product) throws IOException {
        Path rootPath;
        String url;
        checkCancelled();
        currentProduct = product;
        currentProductProgress = new ProductProgress(0, false);
        String productName = product.getName();
        String localArchiveRoot = getLocalArchiveRoot();
        if (localArchiveRoot == null) {
            throw new IllegalArgumentException("Local archive root not set");
        }
        Path productRepositoryPath = Paths.get(localArchiveRoot);
        Path destinationPath = FileUtilities.ensureExists(Paths.get(destination, productName + ".SAFE"));
        Path productSourcePath = findProductPath(productRepositoryPath, product);
        if (productSourcePath == null) {
            logger.warning(String.format("%s not found locally", productName));
            return null;
        }
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        Path destMetaFile = destinationPath.resolve(helper.getMetadataFileName());
        currentStep = "Metadata";
        logger.fine(String.format("Copying metadata file %s", destMetaFile));
        copyFile(productSourcePath.resolve("metadata.xml"), destMetaFile);
        String version = helper.getVersion();
        product.setEntryPoint(helper.getMetadataFileName());
        if (Files.exists(destMetaFile)) {
            Path destInspireFile = destMetaFile.resolveSibling("INSPIRE.xml");
            Path destManifestFile = destMetaFile.resolveSibling("manifest.safe");
            //Path previewFile = metadataFile.resolveSibling("preview.png");
            List<String> allLines = Files.readAllLines(destMetaFile);
            List<String> metaTileNames = Utilities.filter(allLines, "<Granule |<Granules ");

            Set<String> tileIds = updateMetadata(destMetaFile, allLines);
            if (tileIds != null) {
                currentProduct.addAttribute("tiles", String.join(",", tileIds));
                FileUtilities.linkFile(productSourcePath.resolve("inspire.xml"), destInspireFile);
                //downloadFile(baseProductUrl + "inspire.xml", inspireFile);
                FileUtilities.linkFile(productSourcePath.resolve("manifest.safe"), destManifestFile);
                //downloadFile(baseProductUrl + "manifest.safe", manifestFile);

                // rep_info folder and contents
                Path repFolder = FileUtilities.ensureExists(destinationPath.resolve("rep_info"));
                Path schemaFile = repFolder.resolve("S2_User_Product_Level-1C_Metadata.xsd");
                copyFromResources(String.format("S2_User_Product_Level-1C_Metadata%s.xsd", version), schemaFile);
                // HTML folder and contents
                Path htmlFolder = FileUtilities.ensureExists(destinationPath.resolve("HTML"));
                copyFromResources("banner_1.png", htmlFolder);
                copyFromResources("banner_2.png", htmlFolder);
                copyFromResources("banner_3.png", htmlFolder);
                copyFromResources("star_bg.jpg", htmlFolder);
                copyFromResources("UserProduct_index.html", htmlFolder);
                copyFromResources("UserProduct_index.xsl", htmlFolder);

                Path tilesFolder = FileUtilities.ensureExists(destinationPath.resolve(FOLDER_GRANULE));
                FileUtilities.ensureExists(destinationPath.resolve(FOLDER_AUXDATA));
                Path dataStripFolder = FileUtilities.ensureExists(destinationPath.resolve(FOLDER_DATASTRIP));
                Path productJson = productSourcePath.resolve("productInfo.json");
                JsonReader reader = null;
                try {
                    logger.fine(String.format("Reading json product descriptor %s", productJson));
                    reader = Json.createReader(Files.newInputStream(productJson));
                    logger.fine(String.format("Parsing json descriptor %s", productJson));
                    JsonObject obj = reader.readObject();
                    final Map<String, String> tileNames = getTileNames(obj,
                                                                       productRepositoryPath.getParent().toString(),
                                                                       metaTileNames, version, tileIds);
                    String dataStripId = null;
                    String count = String.valueOf(tileNames.size());
                    int tileCounter = 1;
                    List<String> downloadedTiles = new ArrayList<>();
                    for (Map.Entry<String, String> entry : tileNames.entrySet()) {
                        currentStep = "Tile " + String.valueOf(tileCounter++) + "/" + count;
                        Path tileUrl = Paths.get(entry.getValue());
                        String tileName = entry.getKey();
                        Path tileFolder = FileUtilities.ensureExists(tilesFolder.resolve(tileName));
                        Path auxData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_AUXDATA));
                        Path imgData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_IMG_DATA));
                        Path qiData = FileUtilities.ensureExists(tileFolder.resolve(FOLDER_QI_DATA));
                        String metadataName = helper.getGranuleMetadataFileName(tileName);
                        Path tileMetadataPath = tileFolder.resolve(metadataName);
                        logger.fine(String.format("Linking tile metadata %s", tileMetadataPath));
                        FileUtilities.linkFile(tileUrl.resolve("metadata.xml"), tileMetadataPath);
                        //downloadFile(tileUrl + "/metadata.xml", tileMetadataPath);
                        for (String bandFileName : l1cBandFiles) {
                            try {
                                Path bandFileUrl = tileUrl.resolve(bandFileName);
                                Path path = imgData.resolve(helper.getBandFileName(tileName, bandFileName));
                                logger.fine(String.format("Linking band raster %s from %s", path, bandFileName));
                                FileUtilities.linkFile(bandFileUrl, path);
                                //downloadFile(bandFileUrl, path);
                            } catch (IOException ex) {
                                logger.warning(String.format("Linkage for %s failed [%s]", bandFileName, ex.getMessage()));
                            }
                        }
                        List<String> tileMetadataLines = Files.readAllLines(tileMetadataPath);
                        List<String> lines = Utilities.filter(tileMetadataLines, "<MASK_FILENAME");
                        for (String line : lines) {
                            line = line.trim();
                            int firstTagCloseIdx = line.indexOf(">") + 1;
                            int secondTagBeginIdx = line.indexOf("<", firstTagCloseIdx);
                            String maskFileName = line.substring(firstTagCloseIdx, secondTagBeginIdx);
                            String remoteName;
                            Path path;
                            if ("13".equals(version)) {
                                String[] tokens = maskFileName.split(NAME_SEPARATOR);
                                remoteName = tokens[2] + NAME_SEPARATOR + tokens[3] + NAME_SEPARATOR + tokens[9] + ".gml";
                                path = qiData.resolve(maskFileName);
                            } else {
                                remoteName = maskFileName.substring(maskFileName.lastIndexOf(URL_SEPARATOR) + 1);
                                path = destinationPath.resolve(maskFileName);
                            }

                            try {
                                Path fileUrl = tileUrl.resolve("qi").resolve(remoteName);
                                logger.fine(String.format("Linking file %s from %s", path, fileUrl));
                                FileUtilities.linkFile(fileUrl, path);
                                //downloadFile(fileUrl, path);
                            } catch (IOException ex) {
                                logger.warning(String.format("Linkage for %s failed [%s]", path, ex.getMessage()));
                            }
                        }
                        downloadedTiles.add(tileName);
                        logger.fine(String.format("Trying to link %s", tileUrl.resolve("auxiliary/ECMWFT")));
                        FileUtilities.linkFile(tileUrl.resolve("auxiliary/ECMWFT"), auxData.resolve(helper.getEcmWftFileName(tileName)));
                        //downloadFile(tileUrl + "/auxiliary/ECMWFT", auxData.resolve(helper.getEcmWftFileName(tileName)));
                        if (dataStripId == null) {
                            Path tileJson = tileUrl.resolve("tileInfo.json");
                            JsonReader tiReader = null;
                            try {
                                logger.fine(String.format("Reading json tile descriptor %s", tileJson));
                                tiReader = Json.createReader(Files.newInputStream(tileJson));
                                logger.fine(String.format("Parsing json tile descriptor %s", tileJson));
                                JsonObject tileObj = tiReader.readObject();
                                dataStripId = tileObj.getJsonObject("datastrip").getString("id");
                                String dataStripPath = tileObj.getJsonObject("datastrip").getString("path") + "/metadata.xml";
                                Path dataStrip = FileUtilities.ensureExists(dataStripFolder.resolve(helper.getDatastripFolder(dataStripId)));
                                String dataStripFile = helper.getDatastripMetadataFileName(dataStripId);
                                FileUtilities.ensureExists(dataStrip.resolve(FOLDER_QI_DATA));
                                logger.fine(String.format("Linking %s", productRepositoryPath.getParent().resolve(dataStripPath)));
                                FileUtilities.linkFile(productRepositoryPath.getParent().resolve(dataStripPath), dataStrip.resolve(dataStripFile));
                                //downloadFile(baseUrl + dataStripPath, dataStrip.resolve(dataStripFile));
                            } finally {
                                if (tiReader != null) tiReader.close();
                            }
                        }
                    }
                    if (downloadedTiles.size() > 0) {
                        final Pattern tilePattern = helper.getTilePattern();
                        product.addAttribute("tiles", String.join(",", downloadedTiles.stream().map(t -> {
                            Matcher matcher = tilePattern.matcher(t);
                            //noinspection ResultOfMethodCallIgnored
                            matcher.matches();
                            return matcher.group(1);
                        }).collect(Collectors.toList())));
                    }
                } finally {
                    if (reader != null) reader.close();
                }
            } else {
                //Files.deleteIfExists(metadataFile);
                // remove the entire directory
                FileUtilities.deleteTree(destinationPath);
                destinationPath = null;
                logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
                throw new NoSuchElementException(String.format("The product %s did not contain any tiles from the tile list", productName));
            }
        } else {
            // remove the entire directory
            FileUtilities.deleteTree(destinationPath);
            logger.warning(String.format("Either the product %s was not found in the data bucket or the metadata file could not be downloaded", productName));
            destinationPath = null;
        }
        return destinationPath;
    }

    @Override
    protected Path link(EOProduct product, Path sourceRoot, Path targetRoot) throws IOException {
        return link(product);
    }

    @Override
    protected Path check(EOProduct product) throws IOException {
        String productName = product.getName();
        String localArchiveRoot = getLocalArchiveRoot();
        if (localArchiveRoot == null) {
            throw new IllegalArgumentException("Local archive root not set");
        }
        Path productRepositoryPath = Paths.get(localArchiveRoot);
        Path destinationPath = null;
        Path productSourcePath = findProductPath(productRepositoryPath, product);
        if (productSourcePath == null) {
            logger.warning(String.format("%s not found locally", productName));
            return null;
        }
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productName);
        Path metadataFile = productSourcePath.resolve(helper.getMetadataFileName());
        currentStep = "Metadata";
        if (checkFile(productSourcePath.resolve(metadataFile.getFileName()))) {
            List<String> allLines = Files.readAllLines(metadataFile);
            Set<String> tileNames = updateMetadata(metadataFile, allLines);
            if (tileNames != null) {
                final Pattern tilePattern = helper.getTilePattern();
                currentProduct.addAttribute("tiles", String.join(",", tileNames.stream().map(t -> {
                    Matcher matcher = tilePattern.matcher(t);
                    //noinspection ResultOfMethodCallIgnored
                    matcher.matches();
                    return matcher.group(1);
                }).collect(Collectors.toList())));
                //currentProduct.addAttribute("tiles", StringUtils.join(tileNames, ","));
                List<Path> folders = FileUtilities.listFolders(productSourcePath);
                boolean checked = folders.stream()
                                         .filter(folder -> !folder.toString().contains("GRANULE") ||
                                                 "GRANULE".equals(folder.getName(folder.getNameCount() - 1).toString()) ||
                                                 tileNames.stream().anyMatch(tn -> folder.toString().contains(tn)))
                                         .allMatch(folder -> checkFile(folder) &&
                                                 FileUtilities.listFiles(folder).size() > 0);
                if (checked)                            {
                    destinationPath = productSourcePath;
                }
            } else {
                Files.deleteIfExists(metadataFile);
                logger.warning(String.format("The product %s did not contain any tiles from the tile list", productName));
            }
        } else {
            logger.warning(String.format("Either the product %s was not found or the metadata file could not be downloaded",
                                         productName));
        }
        return destinationPath;
    }

    private Map<String, String> getTileNames(JsonObject productInfo, String basePath, List<String> metaTileNames, String psdVersion, Set<String> tileIds) {
        Map<String, String> ret = new HashMap<>();
        JsonArray tiles = productInfo.getJsonArray("tiles");
        for (JsonObject result : tiles.getValuesAs(JsonObject.class)) {
            String tilePath = result.getString("path");
            String[] tokens = tilePath.split(URL_SEPARATOR);
            String simpleTileId = tokens[1] + tokens[2] + tokens[3];
            String tileId = "T" + simpleTileId;
            String tileName = Utilities.find(metaTileNames, tileId, psdVersion);
            if (tileIds.contains(simpleTileId)) {
                ret.put(tileName, basePath + (!basePath.endsWith("/") ? "/" : "") + tilePath);
            }
        }
        return ret;
    }

    private void copyFromResources(String fileName, Path file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(fileName)))) {
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            if (Files.isDirectory(file)) {
                FileUtilities.ensurePermissions(Files.write(file.resolve(fileName), builder.toString().getBytes()));
            } else {
                FileUtilities.ensurePermissions(Files.write(file, builder.toString().getBytes()));
            }
        }
    }

    private Set<String> updateMetadata(Path metaFile, List<String> originalLines) throws IOException {
        Set<String> extractedTileNames = null;
        if (shouldFilterTiles && this.fetchMode != FetchMode.CHECK) {
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
        } else {
            if (tileIdPattern == null) {
                Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(currentProduct.getName());
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
}
