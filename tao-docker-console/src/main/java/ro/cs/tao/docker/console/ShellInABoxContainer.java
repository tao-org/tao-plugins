package ro.cs.tao.docker.console;

import ro.cs.tao.topology.docker.BaseSingletonInstaller;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone Docker plugin for hosting "Shell in a Box" SSH web console.
 *
 * @author Cosmin Cara
 */
public class ShellInABoxContainer extends BaseSingletonInstaller {

    public ShellInABoxContainer() {
        super();
    }

    @Override
    public String getContainerName() { return "spali/shellinabox"; }

    @Override
    public String getContainerDescription() { return "SSH Web Console"; }

    @Override
    protected String logoFile() { return "sib_logo.png"; }

    @Override
    protected String descriptor() { return "sib_container.json"; }

    @Override
    protected List<String> startupArguments() {
        // --name shellinabox -p 4200:4200
        // -e SHELLINABOX_SERVICE_HOST=host
        // -e SHELLINABOX_SERVICE_WHO=who
        // -e SHELLINABOX_SERVICE_LOCAL=local
        // -e SHELLINABOX_ALLOW_SUDO=1
        // -e SHELLINABOX_USER=myuser
        // -e SHELLINABOX_PASSWORD=mypassword
        // -e SHELLINABOX_DISABLE_SSL=1
        // -e SHELLINABOX_DEFAULT=host
        return new ArrayList<String>() {{
            add("--rm"); add("--name"); add("webshell"); add("-p"); add("4200:4200");
            add("-e"); add("SHELLINABOX_SERVICE_HOST=host");
            add("-e"); add("SHELLINABOX_SERVICE_WHO=who");
            add("-e"); add("SHELLINABOX_SERVICE_LOCAL=local");
            add("-e"); add("SHELLINABOX_ALLOW_SUDO=1");
            add("-e"); add("SHELLINABOX_USER=siabuser");
            add("-e"); add("SHELLINABOX_PASSWORD=siabpassword");
            add("-e"); add("SHELLINABOX_DISABLE_SSL=1");
            add("-e"); add("SHELLINABOX_DEFAULT=host");
        }};
    }
}
