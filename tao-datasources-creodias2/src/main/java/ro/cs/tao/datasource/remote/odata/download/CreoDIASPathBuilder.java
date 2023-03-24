package ro.cs.tao.datasource.remote.odata.download;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Path;
import java.nio.file.Paths;
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
        String s3path = product.getAttributeValue("s3path");
        return s3path != null ? Paths.get(s3path) : null;
    }
}
