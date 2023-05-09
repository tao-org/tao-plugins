package ro.cs.tao.datasource.remote.creodias.download;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

public class CreoDIASPathBuilder extends DefaultProductPathBuilder {
    public CreoDIASPathBuilder(Path repositoryPath, String localPathFormat, Properties properties) {
        super(repositoryPath, localPathFormat, properties);
    }

    public CreoDIASPathBuilder(Path repositoryPath, String localPathFormat, Properties properties, boolean testOnly) {
        super(repositoryPath, localPathFormat, properties, testOnly);
    }

    @Override
    public Path getProductPath(Path repositoryPath, EOProduct product) {
        // Products are assumed to be organized according to the pattern defined in tao.properties
        LocalDateTime date = product.getAcquisitionDate();
        String productName = getProductName(product);
        Path productFolderPath = dateToPath(this.repositoryPath.resolve(product.getAttributeValue("productType")),
                                            date, this.localPathFormat);
        Path fullProductPath = productFolderPath.resolve(productName);
        logger.fine(String.format("Looking for product %s into %s", product.getName(), fullProductPath));
        if (!testOnly && !Files.exists(fullProductPath)) {
            // Maybe it's an archived product
            // maybe products are grouped by processing date
            date = product.getProcessingDate();
            if (date != null) {
                productFolderPath = dateToPath(this.repositoryPath.resolve(product.getAttributeValue("productType")),
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
