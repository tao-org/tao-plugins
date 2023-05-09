package ro.cs.tao.ftp;

import com.github.robtimus.filesystems.ftp.FTPEnvironment;
import com.github.robtimus.filesystems.ftp.FTPSEnvironment;

public class FTPSStorageService extends FTPBaseService {

    @Override
    protected String protocol() {
        return "ftps";
    }

    @Override
    protected FTPEnvironment newEnvironment() {
        return new FTPSEnvironment();
    }
}
