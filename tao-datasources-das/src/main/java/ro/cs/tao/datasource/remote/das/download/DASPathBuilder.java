package ro.cs.tao.datasource.remote.das.download;

import ro.cs.tao.datasource.DefaultProductPathBuilder;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.eodata.EOProduct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class DASPathBuilder extends DefaultProductPathBuilder {
    public DASPathBuilder(Path repositoryPath, String localPathFormat, Properties properties) {
        super(repositoryPath, localPathFormat, properties);
    }

    public DASPathBuilder(Path repositoryPath, String localPathFormat, Properties properties, boolean testOnly) {
        super(repositoryPath, localPathFormat, properties, testOnly);
    }

    @Override
     public Path getProductPath(Path repositoryPath, EOProduct product) {
        String mode = product.getAttributeValue("fetch");
        if (mode != null && FetchMode.CHECK.name().equals(mode)) {
            if (!Boolean.parseBoolean(this.properties.getProperty("use.s3path", "true"))) {
                String fileName = product.getName();
                if (".zip".equals(this.properties.getProperty("path.suffix", "folder"))) {
                    fileName = product.getName().endsWith(".SAFE") ?
                            product.getName().replace(".SAFE", ".zip") :
                            product.getName() + ".zip";
                }
                Path localRepoPath = repositoryPath;
                if (Boolean.parseBoolean(this.properties.getProperty("use.target.path.as.local.root", "false"))) {
                    String targetPath = product.getAttributeValue("targetPath");
                    localRepoPath = targetPath != null ? Paths.get(targetPath) : null;
                }
                return localRepoPath != null ? localRepoPath.resolve(fileName) : null;
            }
        }
        String s3path = product.getAttributeValue("s3path");
        return s3path != null ? Paths.get(s3path) : null;
    }

}
