package ro.cs.tao.topology.openstack;

import java.util.List;

public class CloudConfigHelper {

    private final StringBuilder content;
    private final char newLine;

    public CloudConfigHelper() {
        this.content = new StringBuilder("#cloud-config");
        this.newLine = '\n';
    }

    public void addNoUsers() {
        addNewLine();
        addNewLine(0, "users:");
    }

    public void addGroupUser(String groupName) {
        addNewLine();
        addNewLine(0, "groups:");
        addNewLine(1, "- " + groupName);
    }

    public void addDefaultUserToUsers() {
        addNewLine();
        addNewLine(0, "users:");
        addNewLine(1, "- default");
    }

    public void addDefaultUser(String userName, String groupName, String sshPublicKeyContent) {
        addNewLine();
        addNewLine(0, "system_info:");
        addNewLine(1, "default_user:");
        addNewLine(2, "name: " + userName);
        addNewLine(2, "gecos: " + "Default user");
        addNewLine(2, "primary_group: " + groupName);
        //addNewLine(2, "groups: " + "wheel,adm,systemd-journal");
        addNewLine(2, "sudo: " + "[\"ALL=(ALL) NOPASSWD:ALL\"]");
        addNewLine(2, "shell: " + "/bin/bash");
        addNewLine(2, "ssh_pwauth: " + "True");
        addNewLine(2, "lock_passwd: " + "False");
        if (sshPublicKeyContent != null) {
            addNewLine(2, "ssh_authorized_keys: ");
            addNewLine(3, "- " + sshPublicKeyContent);
        }
    }

    public void addChangeUserPasswords(String rootPassword, String username, String password) {
        addNewLine();
        addNewLine(0, "chpasswd:");
        addNewLine(1, "list: |");
        addNewLine(2, "root" + ":" + rootPassword);
        addNewLine(2, username + ":" + password);
        addNewLine(1, "expire" + ": " + "False");
    }

    public void addRunCommands(List<String> commands) {
        addNewLine();
        addNewLine(0, "runcmd:");
        for (int i=0; i<commands.size(); i++) {
            addNewLine(1, "- " + commands.get(i));
        }
    }

    public String getContent() {
        return this.content.toString();
    }

    private void addNewLine(int levelIndex, String line) {
        addNewLine();
        int levelLength = 2;
        for (int i=0; i<levelIndex; i++) {
            for (int k=0; k<levelLength; k++) {
                this.content.append(' ');
            }
        }
        this.content.append(line);
    }

    private void addNewLine() {
        this.content.append(this.newLine);
    }
}
