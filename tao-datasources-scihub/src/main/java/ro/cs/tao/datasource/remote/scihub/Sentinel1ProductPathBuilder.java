package ro.cs.tao.datasource.remote.scihub;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class Sentinel1ProductPathBuilder extends DefaultProductPathBuilder {

    public Sentinel1ProductPathBuilder(Path repositoryPath, String localPathFormat) {
        super(repositoryPath, localPathFormat, null);
    }

    @Override
    public Path getProductPath(Path repositoryPath, EOProduct product) {
        // Products are assumed to be organized according to the pattern defined in tao.properties
        Date date = product.getAcquisitionDate();
        final String productName = product.getAttributeValue("filename") != null ?
                product.getAttributeValue("filename") : product.getName();
        Path productFolderPath = dateToPath(this.repositoryPath, date, this.localPathFormat);
        Path fullProductPath = productFolderPath.resolve(productName);
        if (!Files.exists(fullProductPath)) {
            // Maybe it's an archived product in the sensing date folder
            fullProductPath = productFolderPath.resolve(productName.replace(".SAFE", "").concat(".zip"));
            if (!Files.exists(fullProductPath)) {
                // maybe products are grouped by processing date
                date = product.getProcessingDate();
                if (date != null) {
                    productFolderPath = dateToPath(this.repositoryPath, date, this.localPathFormat);
                    fullProductPath = productFolderPath.resolve(productName);
                    if (!Files.exists(fullProductPath)) {
                        //Maybe it's an archived product in the processing date folder
                        fullProductPath = productFolderPath.resolve(productName.replace(".SAFE", "").concat(".zip"));
                        if (!Files.exists(fullProductPath)) {
                            fullProductPath = null;
                        }
                    }
                } else {
                    fullProductPath = null;
                }
            }
        }
        return fullProductPath;
    }
}
