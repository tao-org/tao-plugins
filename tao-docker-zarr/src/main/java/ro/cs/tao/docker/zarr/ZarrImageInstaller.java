package ro.cs.tao.docker.zarr;

import ro.cs.tao.topology.docker.BaseImageInstaller;

public class ZarrImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "zarr-1-0-0";
    }

    @Override
    protected String getDescription() {
        return "ZARR";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/bin";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "zarr_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "zarr_applications.json";
    }

    @Override
    protected String getLogoFileName() {
        return "zarr_logo.png";
    }

    @Override
    protected String[] additionalResources() {
        return new String[] {
                "gdal_to_xarray.py", "requirements.txt", "union_zarrs.py", "compute_products.py",
                "db_reader.py", "launcher.py", "launcher_union.py", "stack_products.py", "union_chunks.py"
        };
    }
}
