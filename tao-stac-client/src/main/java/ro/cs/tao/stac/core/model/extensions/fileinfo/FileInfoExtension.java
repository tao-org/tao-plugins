package ro.cs.tao.stac.core.model.extensions.fileinfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;

import java.util.logging.Logger;

public class FileInfoExtension<E extends Extensible> extends Extension<E> {

    public FileInfoExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return FileInfoFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        try {
            if (FileInfoFields.BYTE_ORDER.equals(name)) {
                setByteOrder(JsonValueHelper.getString(node, name));
            } else if (FileInfoFields.CHECKSUM.equals(name)) {
                setCheckSum(JsonValueHelper.getString(node, name));
            } else if (FileInfoFields.HEADER_SIZE.equals(name)) {
                setHeaderSize(JsonValueHelper.getInt(node, name));
            } else if (FileInfoFields.SIZE.equals(name)) {
                setSize(JsonValueHelper.getLong(node, name));
            } else if (FileInfoFields.LOCAL_PATH.equals(name)) {
                setLocalPath(JsonValueHelper.getString(node, name));
            }
        } catch (Exception e) {
            Logger.getLogger(FileInfoExtension.class.getName()).warning("Cannot extract field " + name + ": " + e.getMessage());
        }
    }

    public String getByteOrder() {
        return parent.getField(FileInfoFields.BYTE_ORDER);
    }

    public void setByteOrder(String byteOrder) {
        parent.addField(FileInfoFields.BYTE_ORDER, byteOrder);
    }

    public String getCheckSum() {
        return parent.getField(FileInfoFields.CHECKSUM);
    }

    public void setCheckSum(String checkSum) {
        parent.addField(FileInfoFields.BYTE_ORDER, checkSum);
    }

    public int getHeaderSize() {
        return parent.getField(FileInfoFields.HEADER_SIZE);
    }

    public void setHeaderSize(int headerSize) {
        parent.addField(FileInfoFields.HEADER_SIZE, headerSize);
    }

    public long getSize() {
        return parent.getField(FileInfoFields.SIZE);
    }

    public void setSize(long size) {
        parent.addField(FileInfoFields.SIZE, size);
    }

    public String getLocalPath() {
        return parent.getField(FileInfoFields.LOCAL_PATH);
    }

    public void setLocalPath(String localPath) {
        parent.addField(FileInfoFields.LOCAL_PATH, localPath);
    }
}
