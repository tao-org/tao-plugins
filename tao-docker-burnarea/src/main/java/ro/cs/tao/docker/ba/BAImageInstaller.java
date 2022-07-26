package ro.cs.tao.docker.ba;

import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.util.List;
import java.util.UUID;

public class BAImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "ba-22-7-2";
    }

    @Override
    protected String getDescription() {
        return "BA";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/share/burned_area";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String[] additionalResources() {
        return new String[] { "ba.py", "model_gnb_ba.pkl" };
    }

    @Override
    protected Container initializeContainer(Container container, String path) {
        Container baContainer;
        try {
            baContainer = readContainerDescriptor("ba_container.json");
            baContainer.setId(container.getId());
            baContainer.setName(container.getName());
            baContainer.setTag(container.getTag());
            baContainer.setApplicationPath(path);
            baContainer.getApplications().forEach(app -> {
                String appPath = app.getPath()/* + (SystemUtils.IS_OS_WINDOWS && (winExtensions.stream()
                                    .noneMatch(e -> getPathInContainer().toLowerCase().endsWith(e))) ? ".bat" : "")*/;
                app.setName(app.getName());
                app.setPath(appPath);
            });
            //baContainer.setLogo(readContainerLogo("gdal_logo.png"));
            baContainer = this.containerProvider.save(baContainer);
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }
        ProcessingComponent current = null;
        try {
            final ProcessingComponent[] components = readComponentDescriptors("ba_applications.json");
            final List<Application> containerApplications = baContainer.getApplications();
            for (ProcessingComponent component : components) {
                try {
                    current = component;
                    component.setContainerId(baContainer.getId());
                    component.setLabel(component.getId());
                    component.setComponentType(ProcessingComponentType.EXECUTABLE);
                    final Application application = containerApplications.stream().filter(a -> a.getName().equals(component.getId())).findFirst().orElse(null);
                    if (application == null) {
                        logger.severe(String.format("Component [%s] was not found among container [%s] applications",
                                                    component.getId(), container.getName()));
                        continue;
                    }
                    component.setFileLocation(application.getPath());
                    List<ParameterDescriptor> parameterDescriptors = component.getParameterDescriptors();
                    if (parameterDescriptors != null) {
                        parameterDescriptors.forEach(p -> {
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
                    }
                    List<SourceDescriptor> sources = component.getSources();
                    if (sources != null) {
                        sources.forEach(s -> s.setId(UUID.randomUUID().toString()));
                    }
                    List<TargetDescriptor> targets = component.getTargets();
                    if (targets != null) {
                        targets.forEach(t -> t.setId(UUID.randomUUID().toString()));
                    }
                    String template = component.getTemplateContents();
                    int i = 0;
                    while (i < template.length()) {
                        char ch = template.charAt(i);
                        if (ch == '$' && template.charAt(i - 1) != '\n') {
                            template = template.substring(0, i) + "\n" + template.substring(i);
                        }
                        i++;
                    }
                    String[] tokens = template.split("\n");
                    for (int j = 0; j < tokens.length; j++) {
                        final int idx = j;
                        if ((targets != null && targets.stream().anyMatch(t -> t.getName().equals(tokens[idx].substring(1)))) ||
                                (sources != null && sources.stream().anyMatch(s -> s.getName().equals(tokens[idx].substring(1))))) {
                            tokens[j + 1] = tokens[j].replace('-', '$');
                            j++;
                        }
                    }
                    component.setTemplateContents(String.join("\n", tokens));
                    component.setComponentType(ProcessingComponentType.EXECUTABLE);
                    component.setVisibility(ProcessingComponentVisibility.SYSTEM);
                    component.setOwner(SystemPrincipal.instance().getName());
                    component.addTags(getOrCreateTag(container.getName()).getText());
                    if (this.componentProvider.get(component.getId(), component.getContainerId()) == null) {
                        this.componentProvider.save(component);
                    } else {
                        this.componentProvider.update(component);
                    }
                } catch (Exception inner) {
                    logger.severe(String.format("Faulty component: %s. Error: %s",
                                                current != null ? current.getId() : "n/a",
                                                inner.getMessage()));
                }
            }
        } catch (Exception outer) {
            logger.severe(String.format("Error occured while registering container applications: %s",
                                        outer.getMessage()));
        }
        return baContainer;
    }
}
