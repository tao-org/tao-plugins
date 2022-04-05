import ro.cs.tao.aws.S3StorageService;
import ro.cs.tao.services.model.FileObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestAWSS3 {

    public static void main(String[] args) throws IOException {
        final String region = "eu-central-1";
        final String bucket = "agriculture-vlab-data-staging";
        final S3StorageService client = new S3StorageService();
        final Map<String, String> params = new HashMap<String, String>() {{
            put(S3StorageService.AWS_REGION, region);
            put(S3StorageService.AWS_BUCKET, bucket);
            put(S3StorageService.AWS_ACCESS_KEY, "AKIA4D6IV3TWJTHJXAKS");
            put(S3StorageService.AWS_SECRET_KEY, "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");
        }};
        List<FileObject> list = client.listUserWorkspace();
        for (FileObject fileObject : list) {
            System.out.println(fileObject.getRelativePath() + ", folder? " + fileObject.isFolder() + ", size " + fileObject.getSize());
        }
        System.exit(0);
    }
}
