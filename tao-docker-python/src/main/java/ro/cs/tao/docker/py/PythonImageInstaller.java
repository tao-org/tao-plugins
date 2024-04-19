/*
 * Copyright (C) 2018 CS ROMANIA
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
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;

public class PythonImageInstaller extends BaseImageInstaller {
    @Override
    public String getContainerName() { return "python-2-7-15"; }

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
    protected String getContainerDescriptorFileName() {
        return "python_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "python_applications.json";
    }

    @Override
    protected String getLogoFileName() {
        return "python_logo.png";
    }
}
