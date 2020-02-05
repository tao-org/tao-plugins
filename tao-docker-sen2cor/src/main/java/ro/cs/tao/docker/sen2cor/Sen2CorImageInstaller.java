package ro.cs.tao.docker.sen2cor;

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

public class Sen2CorImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "sen2cor-2-8";
    }

    @Override
    protected String getDescription() {
        return "Sen2Cor";
    }

    @Override
    protected String getPathInContainer() {
        return "/opt/sen2cor/bin";
    }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "L2A_Process.bat" : "L2A_Process");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected Container initializeContainer(Container container, String path) {
        Container sen2CorContainer = null;
        try {
            sen2CorContainer = readContainerDescriptor("sen2cor_container.json");
            sen2CorContainer.setId(container.getId());
            sen2CorContainer.setName(container.getName());
            sen2CorContainer.setTag(container.getTag());
            sen2CorContainer.setApplicationPath(path);
            sen2CorContainer = this.containerProvider.save(sen2CorContainer);
            ProcessingComponent[] components = readComponentDescriptors("sen2cor_operators.json");
            for (ProcessingComponent component : components) {
                try {
                    component.setContainerId(sen2CorContainer.getId());
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
                    this.componentProvider.save(component);
                } catch (Exception inner) {
                    logger.severe(String.format("Faulty component: %s. Error: %s",
                                                component != null ? component.getId() : "n/a",
                                                inner.getMessage()));
                }
            }
        } catch (Exception outer) {
            logger.severe(String.format("Error occured while registering container applications: %s",
                                        outer.getMessage()));
        }
        return sen2CorContainer;
    }
}
