/*
 * Copyright (C) 2017 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.docker.otb;

import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.utils.Platform;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class OTBImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() { return "otb-6-4-0"; }

    @Override
    protected String getDescription() { return "OTB"; }

    @Override
    protected String getPathInContainer() { return "/opt/OTB-6.4.0-Linux64/bin"; }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(Platform.getCurrentPlatform().getId().equals(Platform.ID.win) ?
                                       "otbcli_BandMath.bat" : "otbcli_BandMath");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected Container initializeContainer(String containerId, String path) {
        PersistenceManager persistenceManager = getPersistenceManager();
        Container otbContainer = null;
        try {
            otbContainer = persistenceManager.getContainerById(containerId);
        } catch (PersistenceException ignored) { }
        boolean isWin = Platform.getCurrentPlatform().getId().equals(Platform.ID.win);
        if (otbContainer == null) {
            try {
                otbContainer = readContainerDescriptor("otb_container.json");
                otbContainer.setId(containerId);
                otbContainer.setName(getContainerName());
                otbContainer.setTag(getContainerName());
                otbContainer.setApplicationPath(path);
                otbContainer.getApplications().forEach(app -> {
                    String appPath = app.getPath() + (isWin && (winExtensions.stream()
                                        .noneMatch(e -> getPathInContainer().toLowerCase().endsWith(e))) ? ".bat" : "");
                    app.setName(app.getName());
                    app.setPath(appPath);
                });
                otbContainer.setLogo(readContainerLogo("otb_logo.png"));
                otbContainer = persistenceManager.saveContainer(otbContainer);
            } catch (Exception e) {
                logger.severe(e.getMessage());
            }
            ProcessingComponent current = null;
            try {
                ProcessingComponent[] components = readComponentDescriptors("otb_applications.json");
                List<Application> containerApplications = otbContainer.getApplications();
                for (ProcessingComponent component : components) {
                    current = component;
                    component.setContainerId(otbContainer.getId());
                    component.setLabel(component.getId());
                    component.setComponentType(ProcessingComponentType.EXECUTABLE);
                    component.setFileLocation(containerApplications.stream().filter(a -> a.getName().equals(component.getId())).findFirst().get().getPath());
                    List<ParameterDescriptor> parameterDescriptors = component.getParameterDescriptors();
                    if (parameterDescriptors != null) {
                        parameterDescriptors.forEach(p -> {
                            if (p.getName() == null) {
                                p.setName(p.getId());
                                p.setId(UUID.randomUUID().toString());
                            }
                            String[] valueSet = p.getValueSet();
                            if (valueSet != null && valueSet.length > 0) {
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
                        Character ch = template.charAt(i);
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
                    component.setOwner(SystemPrincipal.instance().getName());
                    persistenceManager.saveProcessingComponent(component);
                }
            } catch (Exception e) {
                logger.severe(String.format("Faulty component: %s. Error: %s",
                                            current != null ? current.getId() : "n/a",
                                            e.getMessage()));
            }
        } else {
            /*otbContainer.setId(containerId);
            otbContainer.setName(containerName);
            otbContainer.setTag(containerName);
            otbContainer.setApplicationPath(path);
            otbContainer.getApplications().forEach(app -> {
                String appPath = app.getName() + (isWin && (winExtensions.stream()
                        .noneMatch(e -> getPathInContainer().toLowerCase().endsWith(e))) ? ".bat" : "");
                app.setPath(appPath);
            });
            try {
                otbContainer = persistenceManager.updateContainer(otbContainer);
            } catch (Exception e) {
                logger.severe(e.getMessage());
            }*/
            logger.info(String.format("Container %s already registered", getContainerName()));
        }
        return otbContainer;
    }
}
