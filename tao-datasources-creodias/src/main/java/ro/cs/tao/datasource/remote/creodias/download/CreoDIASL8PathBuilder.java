package ro.cs.tao.datasource.remote.creodias.download;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

public class CreoDIASL8PathBuilder extends DefaultProductPathBuilder {
    public CreoDIASL8PathBuilder(Path repositoryPath, String localPathFormat, Properties properties) {
        super(repositoryPath, localPathFormat, properties);
    }

    public CreoDIASL8PathBuilder(Path repositoryPath, String localPathFormat, Properties properties, boolean testOnly) {
        super(repositoryPath, localPathFormat, properties, testOnly);
    }

    @Override
    public Path getProductPath(Path repositoryPath, EOProduct product) {
        // Products are assumed to be organized according to the pattern defined in tao.properties
        LocalDateTime date = product.getAcquisitionDate();
        final String productName = getProductName(product);
        final String productType = product.getAttributeValue("productType");
        Path productFolderPath = dateToPath(this.repositoryPath.resolve("OLI_TIRS").resolve(productType),
                                            date, this.localPathFormat);
        Path fullProductPath = productFolderPath.resolve(productName);
        logger.fine(String.format("Looking for product %s into %s", product.getName(), fullProductPath));
        if (!testOnly && !Files.exists(fullProductPath)) {
            // Maybe it's an archived product
            // maybe products are grouped by processing date
            date = product.getProcessingDate();
            if (date != null) {
                productFolderPath = dateToPath(this.repositoryPath.resolve("OLI_TIRS").resolve(productType),
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
