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

package ro.cs.tao.docker.py;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.docker.Container;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class PythonImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() { return "python-2-7-15"; }

    @Override
    protected String getDescription() { return "PYTHON2.7"; }

    @Override
    protected String getPathInContainer() { return "/opt/python"; }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "python.exe" : "python");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected Container initializeContainer(String containerId, String path) {
        PersistenceManager persistenceManager = getPersistenceManager();
        Container container = null;
        try {
            container = persistenceManager.getContainerById(containerId);
        } catch (PersistenceException ignored) { }
        if (container == null) {
            try {
                container = readContainerDescriptor("python_container.json");
                container.setId(containerId);
                container.setName(getContainerName());
                container.setTag(getContainerName());
                container.setApplicationPath(path);
                container.setLogo(readContainerLogo("python_logo.png"));
                container = persistenceManager.saveContainer(container);
                ProcessingComponent[] components = readComponentDescriptors("python_applications.json");
                for (ProcessingComponent component : components) {
                    if (component.getId() == null || component.getId().isEmpty()) {
                        component.setId(UUID.randomUUID().toString());
                    }
                    component.setContainerId(container.getId());
                    component.setComponentType(ProcessingComponentType.SCRIPT);
                    component.setOwner(SystemPrincipal.instance().getName());
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
                    persistenceManager.saveProcessingComponent(component);
                }
            } catch (Exception e) {
                logger.severe(e.getMessage());
            }
        } else {
            logger.info(String.format("Container %s already registered", getContainerName()));
        }
        return container;
    }
}
