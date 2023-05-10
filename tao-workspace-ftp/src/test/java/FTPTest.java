import ro.cs.tao.ftp.FTPStorageService;
import ro.cs.tao.services.model.FileObject;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class FTPTest {

    public static void main(String[] args) throws IOException {
        Repository repository = setupRepository();
        testRepository(repository);
        final FTPStorageService service = new FTPStorageService();
        service.associate(repository);
        List<FileObject> fileObjects = service.listFiles("/neodc", null, null, 1);
        for (FileObject file : fileObjects) {
            System.out.println(file);
        }
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

    private static Repository setupRepository() {
        final Repository repository = new Repository();
        repository.setId(UUID.randomUUID().toString());
        repository.setName("Test");
        repository.setDescription("Description");
        repository.setType(RepositoryType.FTP);
        repository.setReadOnly(true);
        repository.setCreated(LocalDateTime.now());
        repository.setSystem(false);
        repository.setUrlPrefix("ftp");
        repository.setUserName("");
        repository.addParameter("ftp.server", "");
        repository.addParameter("ftp.user", "");
        repository.addParameter("ftp.password", "");
        return repository;
    }
}
