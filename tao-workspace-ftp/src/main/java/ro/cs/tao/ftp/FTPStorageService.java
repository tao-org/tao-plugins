package ro.cs.tao.ftp;

import com.github.robtimus.filesystems.ftp.FTPEnvironment;

public class FTPStorageService extends FTPBaseService {

    @Override
    protected String protocol() {
        return "ftp";
    }

    @Override
    protected FTPEnvironment newEnvironment() {
        return new FTPEnvironment();
    }
}
