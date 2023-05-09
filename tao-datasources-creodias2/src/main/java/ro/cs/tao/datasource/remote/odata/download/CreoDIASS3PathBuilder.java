package ro.cs.tao.datasource.remote.odata.download;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

public class CreoDIASS3PathBuilder extends DefaultProductPathBuilder {
    public CreoDIASS3PathBuilder(Path repositoryPath, String localPathFormat, Properties properties) {
        super(repositoryPath, localPathFormat, properties);
    }

    public CreoDIASS3PathBuilder(Path repositoryPath, String localPathFormat, Properties properties, boolean testOnly) {
        super(repositoryPath, localPathFormat, properties, testOnly);
    }

    @Override
    public Path getProductPath(Path repositoryPath, EOProduct product) {
        // Products are assumed to be organized according to the pattern defined in tao.properties
        LocalDateTime date = product.getAcquisitionDate();
        final String productName = getProductName(product);
        String productType = product.getAttributeValue("productType");
        String instrument;
        if (productType.endsWith("_")) {
            productType = productType.replace("___", "");
            instrument = productType.substring(0, productType.indexOf("_"));
            productType = productType.substring(productType.lastIndexOf("_") + 1);
        } else {
            instrument = product.getAttributeValue("instrument");
        }
        final String pathProductType;
        switch (instrument) {
            case "OL":
                pathProductType = "OLCI";
                break;
            case "SL":
                pathProductType = "SLSTR";
                break;
            case "SR":
                pathProductType = "SRAL";
                break;
            default:
                pathProductType = "SYNERGY";
        }
        String level = product.getAttributeValue("processingLevel");
        if (level.length() > 1) {
            level = level.substring(level.length() - 1);
        }
        Path productFolderPath = dateToPath(this.repositoryPath.resolve(pathProductType).resolve(instrument + "_" + level + "_" + productType),
                                            date, this.localPathFormat);
        Path fullProductPath = productFolderPath.resolve(productName);
        logger.fine(String.format("Looking for product %s into %s", product.getName(), fullProductPath));
        if (!testOnly && !Files.exists(fullProductPath)) {
            // Maybe it's an archived product
            // maybe products are grouped by processing date
            date = product.getProcessingDate();
            if (date != null) {
                productFolderPath = dateToPath(this.repositoryPath.resolve(pathProductType).resolve(instrument + "_" + level + "_" + productType),
                                               date, this.localPathFormat);
                fullProductPath = productFolderPath.resolve(productName);
                logger.fine(String.format("Alternatively looking for product %s into %s", product.getName(), fullProductPath));
                if (!Files.exists(fullProductPath)) {
                    fullProductPath = null;
                }
            } else {
                fullProductPath = null;
            }
        }
        return fullProductPath;
    }
}
