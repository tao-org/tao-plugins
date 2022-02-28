import ro.cs.tao.aws.S3StorageService;
import ro.cs.tao.services.model.FileObject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestAWSS3 {

    public static void main(String[] args) throws IOException {
        final String accessKey = "AKIA4D6IV3TWJTHJXAKS";
        final String secretKey = "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";
        final String region = "eu-central-1";
        final String bucket = "agriculture-vlab-data-staging";
        final S3StorageService client = new S3StorageService();
        final Map<String, String> params = new HashMap<String, String>() {{
            put(S3StorageService.AWS_ACCESS_KEY, accessKey);
            put(S3StorageService.AWS_SECRET_KEY, secretKey);
            put(S3StorageService.AWS_REGION, region);
            put(S3StorageService.AWS_BUCKET, bucket);
        }};
        client.setup(params);
        List<FileObject> list = client.listUserWorkspace(bucket);
        String relativePath = null;
        for (FileObject fileObject : list) {
            System.out.println(fileObject.getRelativePath() + ", folder? " + fileObject.isFolder() + ", size " + fileObject.getSize());
        }
        relativePath = "s2/";
        list = client.listFiles(Paths.get(bucket + "/" + relativePath), null, null);
        for (FileObject fileObject : list) {
            relativePath = fileObject.getRelativePath();
            System.out.println(fileObject.getRelativePath() + ", folder? " + fileObject.isFolder() + ", size " + fileObject.getSize());
        }
        list = client.listFiles(Paths.get(bucket + "/" + relativePath), null, null);
        for (FileObject fileObject : list) {
            System.out.println(fileObject.getRelativePath() + ", folder? " + fileObject.isFolder() + ", size " + fileObject.getSize());
        }
        System.exit(0);
    }
}
