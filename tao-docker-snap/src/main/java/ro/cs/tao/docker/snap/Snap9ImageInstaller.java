package ro.cs.tao.docker.snap;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.docker.Container;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class Snap9ImageInstaller  extends BaseImageInstaller {

    @Override
    protected String getContainerName() { return "snap-9-0-0"; }

    @Override
    protected String getDescription() { return "SNAP"; }

    @Override
    protected String getPathInContainer() { return "/opt/snap/bin"; }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "gpt.exe" : "gpt");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected String[] additionalResources() {
        return new String[] { "update_snap.sh" };
    }

    @Override
    protected Container initializeContainer(Container container, String path) {
        Container snapContainer = null;
        try {
            snapContainer = readContainerDescriptor("snap9_container.json");
            snapContainer.setId(container.getId());
            snapContainer.setName(container.getName());
            snapContainer.setTag(container.getTag());
            snapContainer.setApplicationPath(path);
            snapContainer.getApplications().forEach(a -> {
                if (a.getPath() == null) {
                    a.setPath("gpt");
                }
                /*if (SystemUtils.IS_OS_WINDOWS && !a.getPath().endsWith(".exe")) {
                    a.setPath(a.getPath() + ".exe");
                }*/
                a.setParallelFlagTemplate("-q <integer>");
            });
            snapContainer.setLogo(readContainerLogo("snap9_logo.png"));
            snapContainer = this.containerProvider.save(snapContainer);
            ProcessingComponent[] components = readComponentDescriptors("snap9_operators.json");
            for (ProcessingComponent component : components) {
                try {
                    component.setContainerId(snapContainer.getId());
                    component.setComponentType(ProcessingComponentType.EXECUTABLE);
                    component.setOwner(SystemPrincipal.instance().getName());
                    component.getParameterDescriptors().forEach(p -> {
                        if (p.getName() == null) {
                            p.setName(p.getId());
                            p.setId(UUID.randomUUID().toString());
                        }
                        String[] valueSet = p.getValueSet();
                        if (valueSet != null && valueSet.length == 1 &&
                                ("null".equals(valueSet[0]) || valueSet[0].isEmpty())) {
                            p.setValueSet(null);
                        }
                        if (valueSet != null && valueSet.length > 0 &&
                                ("null".equals(valueSet[0]) || valueSet[0].isEmpty())) {
                            p.setDefaultValue(valueSet[0]);
                        }
                    });
                    List<SourceDescriptor> sources = component.getSources();
                    if (sources != null) {
                        sources.forEach(s -> {
                            if (s.getId() == null || s.getId().isEmpty()) {
                                s.setId(UUID.randomUUID().toString());
                            }
                        });
                    }
                    List<TargetDescriptor> targets = component.getTargets();
                    if (targets != null) {
                        targets.forEach(t -> {
                            if (t.getId() == null || t.getId().isEmpty()) {
                                t.setId(UUID.randomUUID().toString());
                            }
                        });
                    }
                    component.setVisibility(ProcessingComponentVisibility.SYSTEM);
                    component.addTags(getOrCreateTag(container.getName()).getText());
                    if (this.componentProvider.get(component.getId(), component.getContainerId()) == null) {
                        this.componentProvider.save(component);
                    } else {
                        this.componentProvider.update(component);
                    }
                } catch (Exception inner) {
                    logger.severe(String.format("Faulty component: %s. Error: %s",
                                                component != null ? component.getId() : "n/a",
                                                inner.getMessage()));
                }
            }
        } catch (Exception outer) {
            logger.severe(String.format("Error occurred while registering container applications: %s",
                                        outer.getMessage()));
        }
        return snapContainer;
    }
}