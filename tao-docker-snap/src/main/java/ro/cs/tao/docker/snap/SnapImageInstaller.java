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

package ro.cs.tao.docker.snap;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.docker.Container;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.utils.Platform;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class SnapImageInstaller extends BaseImageInstaller {

    @Override
    protected String getContainerName() { return "snap-6-0-0"; }

    @Override
    protected String getDescription() { return "SNAP"; }

    @Override
    protected String getPathInContainer() { return "/opt/snap/bin"; }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(Platform.getCurrentPlatform().getId().equals(Platform.ID.win) ? "gpt.exe" : "gpt");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected Container initializeContainer(String containerId, String path) {
        PersistenceManager persistenceManager = getPersistenceManager();
        Container snapContainer = null;
        try {
            snapContainer = persistenceManager.getContainerById(containerId);
        } catch (PersistenceException ignored) { }
        boolean isWin = Platform.getCurrentPlatform().getId().equals(Platform.ID.win);
        if (snapContainer == null) {
            try {
                snapContainer = readContainerDescriptor("snap_container.json");
                snapContainer.setId(containerId);
                snapContainer.setName(getContainerName());
                snapContainer.setTag(getContainerName());
                snapContainer.setApplicationPath(path);
                snapContainer.getApplications().forEach(a -> {
                    if (a.getPath() == null) {
                        a.setPath("gpt");
                    }
                    if (isWin && !a.getPath().endsWith(".exe")) {
                        a.setPath(a.getPath() + ".exe");
                    }
                    a.setParallelFlagTemplate("-q <integer>");
                });
                snapContainer.setLogo(readContainerLogo("snap_logo.png"));
                snapContainer = persistenceManager.saveContainer(snapContainer);
                ProcessingComponent[] components = readComponentDescriptors("snap_operators.json");
                for (ProcessingComponent component : components) {
                    component.setContainerId(snapContainer.getId());
                    component.setComponentType(ProcessingComponentType.EXECUTABLE);
                    component.setOwner(SystemPrincipal.instance().getName());
                    component.getParameterDescriptors().forEach(p -> {
                        if (p.getName() == null) {
                            p.setName(p.getId());
                            p.setId(UUID.randomUUID().toString());
                        }
                        String[] valueSet = p.getValueSet();
                        if (valueSet != null && valueSet.length > 0) {
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
                    persistenceManager.saveProcessingComponent(component);
                }
            } catch (Exception e) {
                logger.severe(e.getMessage());
            }
        } else {
            /*try {
                snapContainer.setId(containerId);
                snapContainer.setName(containerName);
                snapContainer.setTag(containerName);
                snapContainer.setApplicationPath(path);
                snapContainer.getApplications().forEach(a -> {
                    if (a.getPath() == null) {
                        a.setPath("gpt");
                    }
                    if (isWin && !a.getPath().endsWith(".exe")) {
                        a.setPath(a.getPath() + ".exe");
                    }
                    a.setParallelFlagTemplate("-q <integer>");
                });
                snapContainer = persistenceManager.updateContainer(snapContainer);
            } catch (Exception e) {
                logger.severe(e.getMessage());
            }*/
            logger.info(String.format("Container %s already registered", getContainerName()));
        }
        return snapContainer;
    }
}