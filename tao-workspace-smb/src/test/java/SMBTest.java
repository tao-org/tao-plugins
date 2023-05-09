import ro.cs.tao.smb.SMBStorageService;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.time.LocalDateTime;
import java.util.UUID;

public class SMBTest {

    public static void main(String[] args) {
        Repository repository = setupSMBRepository();
        testRepository(repository);
        repository = setupAWSRepository();
        testRepository(repository);
        repository = setupSwiftRepository();
        testRepository(repository);
        repository = setupFileRepository();
        testRepository(repository);
        System.exit(0);
    }

    private static void testRepository(Repository repository) {
        System.out.println("### " + repository.getType().name() + " ###");
        System.out.println("Root = " + repository.root());
        System.out.println("Bucket = " + repository.bucketName());
        String path = "/some/relative/folder/";
        String path2 = "some/relative/folder/subfolder/file.txt";
        System.out.println("Relative path resolved: " + repository.resolve(path));
        System.out.println("Relative path resolved: " + repository.resolve(path2));
        System.out.println("Relative to bucket: " + repository.relativizeToBucket(path));
        System.out.println("Relative to root: " + repository.relativizeToRoot(path));
        System.out.println("Relative to ref path: " + repository.relativize(path, path2));
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
        repository.setUserName("");
        repository.addParameter("smb.server", "");
        repository.addParameter("smb.domain", null);
        repository.addParameter("smb.share", "");
        repository.addParameter("smb.user", "");
        repository.addParameter("smb.password", "");
        final SMBStorageService service = new SMBStorageService();
        service.associate(repository);
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
        repository.setUserName("");
        repository.addParameter("aws.bucket", "");
        repository.addParameter("aws.region", "eu-central-1");
        repository.addParameter("aws.secret.key", "");
        repository.addParameter("aws.access.key", "");
        return repository;
    }

    private static Repository setupSwiftRepository() {
        final Repository repository = new Repository();
        repository.setId(UUID.randomUUID().toString());
        repository.setName("Test");
        repository.setDescription("Description");
        repository.setType(RepositoryType.SWIFT);
        repository.setReadOnly(false);
        repository.setCreated(LocalDateTime.now());
        repository.setSystem(false);
        repository.setUrlPrefix("swift");
        repository.setUserName("");
        repository.addParameter("openstack.tenantId", "");
        repository.addParameter("openstack.domain", "");
        repository.addParameter("openstack.user", "");
        repository.addParameter("openstack.password", "");
        repository.addParameter("openstack.bucket", "");
        repository.addParameter("openstack.auth.url", "3");
        return repository;
    }

    private static Repository setupFileRepository() {
        final Repository repository = new Repository();
        repository.setId(UUID.randomUUID().toString());
        repository.setName("Test");
        repository.setDescription("Description");
        repository.setType(RepositoryType.LOCAL);
        repository.setReadOnly(false);
        repository.setCreated(LocalDateTime.now());
        repository.setSystem(false);
        repository.setUrlPrefix("swift");
        repository.setUserName("");
        repository.addParameter("root", "/mnt/tao/working_dir/");
        return repository;
    }
}
