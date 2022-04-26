import ro.cs.tao.smb.SMBStorageService;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.time.LocalDateTime;
import java.util.UUID;

public class SMBTest {

    public static void main(String[] args) {
        final Repository repository = setupSMBRepository();
        final SMBStorageService service = new SMBStorageService();
        service.associate(repository);
        System.out.println("Root = " + repository.root());
        System.out.println("Bucket = " + repository.bucketName());
        String path = "data5/some/relative/folder/";
        String path2 = "data5/some/relative/folder/subfolder/file.txt";
        System.out.println("Relative path resolved: " + repository.resolve(path));
        System.out.println("Relative path resolved: " + repository.resolve(path2));
        System.out.println("Relative to bucket: " + repository.relativizeToBucket(path));
        System.out.println("Relative to root: " + repository.relativizeToRoot(path));
        System.out.println("Relative to ref path: " + repository.relativize(path, path2));
        System.exit(0);
    }

    private static Repository setupSMBRepository() {
        final Repository repository = new Repository();
        repository.setId(UUID.randomUUID().toString());
        repository.setName("Test");
        repository.setDescription("Description");
        repository.setType(RepositoryType.SMB);
        repository.setReadOnly(false);
        repository.setCreated(LocalDateTime.now());
        repository.setSystem(false);
        repository.setUrlPrefix("smb");
        repository.setUserName("kraftek");
        repository.addParameter("smb.server", "qnap.c-s.ro");
        repository.addParameter("smb.domain", null);
        repository.addParameter("smb.share", "data5");
        repository.addParameter("smb.user", "qnap");
        repository.addParameter("smb.password", "qnap@aeic29");
        return repository;
    }

    private static Repository setupAWSRepository() {
        final Repository repository = new Repository();
        repository.setId(UUID.randomUUID().toString());
        repository.setName("Test");
        repository.setDescription("Description");
        repository.setType(RepositoryType.AWS);
        repository.setReadOnly(false);
        repository.setCreated(LocalDateTime.now());
        repository.setSystem(false);
        repository.setUrlPrefix("s3");
        repository.setUserName("kraftek");
        repository.addParameter("aws.bucket", "agriculture-vlab-data-staging/kraftek");
        repository.addParameter("aws.region", "eu-central-1");
        repository.addParameter("aws.secret.key", "rGmXXLJ0gHo/F03uIAyfhWigkQxsPzYBSn/28sZg");
        repository.addParameter("aws.access.key", "AKIA4D6IV3TWJTHJXAKS");
        return repository;
    }
}
