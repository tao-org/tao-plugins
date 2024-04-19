import ro.cs.tao.aws.S3StorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryFactory;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestAWSS3 {

    public static void main(String[] args) throws IOException {
        final String region = "us-west-2"; //"eu-central-1";
        final String bucket = "sentinel-cogs"; //"agriculture-vlab-data-staging";
        final S3StorageService client = new S3StorageService();
        Repository repository = new Repository();
        repository.setType(RepositoryType.AWS);
        final LinkedHashMap<String, String> params = new LinkedHashMap<String, String>() {{
            put(S3StorageService.AWS_REGION, region);
            put(S3StorageService.AWS_BUCKET, bucket);
            //put(S3StorageService.AWS_ENDPOINT, "https://s3.us-west-2.amazonaws.com");
            //put(S3StorageService.AWS_ACCESS_KEY, "AKIA4D6IV3TWJTHJXAKS");
            //put(S3StorageService.AWS_SECRET_KEY, "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");
        }};
        repository.setParameters(params);
        client.associate(repository);
        List<FileObject> list = client.listUserWorkspace();
        for (FileObject fileObject : list) {
            System.out.println(fileObject.getRelativePath() + ", folder? " + fileObject.isFolder() + ", size " + fileObject.getSize());
        }
        System.exit(0);
    }
}
