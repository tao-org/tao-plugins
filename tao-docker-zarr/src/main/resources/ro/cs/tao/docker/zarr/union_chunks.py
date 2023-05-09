#!/usr/bin/env python3

import argparse
import blosc
import json
import multiprocessing.dummy
import numpy as np
import os
import shutil

ZARRAY_FILE = '.zarray'
ZATTRS_FILE = '.zattrs'
ZGROUP = '.zgroup'
ZMETADATA = '.zmetadata'

# ZARRAY attributes
CHUNKS = 'chunks'
COMPRESSOR = 'compressor'
SHAPE = 'shape'
DTYPE = 'dtype'

# Modes
BAND_APPEND = "BAND_APPEND"
CHUNK_APPEND = "CHUNK_APPEND"
UNION_MODE = "UNION_MODE"

class Variable(object):
    def __init__(self, in_path, out_path, name, shape, chunk, cname, dtype):
        self.in_path = in_path
        self.out_path = out_path
        self.name = name
        self.shape = shape
        self.chunk = chunk
        self.cname = cname
        self.dtype = dtype
        self.mode = None
        self.z_axis = 0

    def __str__(self):
        return self.name + " " + self.path

class VariableCollection(object):
    def __init__(self):
        self.variables = []

    def add(self, variable):
        """Add only the variables whose name has not been encountered yet.
        Args:
            variable (Variable): object containing all the properties of an xarray.DataArray
        """
        should_add = True
        for v in self.variables:
            if v.name == variable.name:
                should_add = False
                return

        if should_add:
            self.variables.append(variable)


class Metadata(object):
    def __init__(self, paths, out_ds_path, ref, is_append, time_chunks):
        self.paths = paths
        self.out_ds_path = out_ds_path
        self.ref = ref
        self.is_append = is_append
        self.time_chunks = time_chunks
        self.variable_collection = VariableCollection()

    def get_vars(self):
        for path in self.paths:
            files = os.listdir(path)
            for f in files:
                if f[0] != '.' and f != 'time':
                    (chunk, shape, cname, dtype) = self.read_zarray(os.path.join(path, f))
                    var_path = os.path.join(path, f)
                    var_out_path = os.path.join(self.out_ds_path, f)
                    variable = Variable(var_path, var_out_path, f, shape, chunk, cname, dtype)
                    if os.path.exists(var_out_path):
                        variable.mode = CHUNK_APPEND
                    else:
                        # os.mkdir(var_out_path)
                        shutil.copytree(var_path, var_out_path)
                        variable.mode = BAND_APPEND

                    self.variable_collection.add(variable)

        vars_list = self.variable_collection.variables
        variables = [v for v in vars_list if len(v.shape) != 3]
        variables_3D = [v for v in vars_list if len(v.shape) == 3]
        return (variables, variables_3D)


    def read_zarray(self, path):
        dtype = None
        metadata_file = os.path.join(path, ZARRAY_FILE)
        cname = None
        with open(metadata_file, 'r') as f:
            metadata = json.load(f)
            if COMPRESSOR in metadata and metadata[COMPRESSOR]:
                cname = metadata[COMPRESSOR]['cname']
            mdtype = metadata[DTYPE]
        dtype = mdtype[1:] if not mdtype[0].isalpha() else mdtype
        return (metadata[CHUNKS], metadata[SHAPE], cname, dtype)

    def handle_time_metadata(self, in_path, out_path, shape, time_chunk):
        if not self.is_append:
            # Copy .zattrs as it is
            shutil.copy(os.path.join(in_path, ZATTRS_FILE), os.path.join(out_path, ZATTRS_FILE))
            # print(f"Copied the .zattrs file from {os.path.join(in_path, ZATTRS_FILE)} to {os.path.join(out_path, ZATTRS_FILE)}")

        # Read .zarray metadata
        with open(os.path.join(in_path, ZARRAY_FILE), 'r') as f:
            metadata = json.load(f)

        # Update metadata
        metadata[SHAPE][0] = int(shape)
        with open(os.path.join(out_path, ZARRAY_FILE), 'w') as f:
            metadata[CHUNKS][0] = int(shape)
            json.dump(metadata, f, indent=4, sort_keys=True)

        # print(f"Metadata now: {metadata}")


    def handle_variable_metadata(self, variable):
        if variable.mode == BAND_APPEND:
            shutil.copy(os.path.join(variable.in_path, ZARRAY_FILE), variable.out_path)
            shutil.copy(os.path.join(variable.in_path, ZATTRS_FILE), os.path.join(variable.out_path, ZATTRS_FILE))

        # Handle .zarray
        # with open(os.path.join(variable.in_path, ZARRAY_FILE), 'r') as f:
        #     metadata = json.load(f)

        out_path = os.path.join(variable.out_path, ZARRAY_FILE)
        with open(out_path, 'r+') as f:
            metadata = json.load(f)
            metadata[SHAPE][0] = int(variable.z_axis)
            metadata[CHUNKS][0] = int(self.time_chunks)
            f.seek(0)
            json.dump(metadata, f, indent=4, sort_keys=True)
            f.truncate()


    def _handle_zmetadata(self):
        json_metadata = { 'metadata' : {} }
        ls_output = [f for f in os.listdir(self.out_ds_path) if f != ZMETADATA]
        for d in ls_output:
            d_path = os.path.join(self.out_ds_path, d)
            if d[0] == '.':
                with open(d_path, 'r') as metadata:
                    element = {d : json.load(metadata)}
                    json_metadata['metadata'].update(element)
            else:
                for g in os.listdir(d_path):
                    if g[0] == '.':
                        with open(os.path.join(d_path, g), 'r') as metadata:
                            key = f"{d}/{g}"
                            element = {key: json.load(metadata)}
                            json_metadata['metadata'].update(element)
        json_metadata['zarr_consolidated_format'] = 1

        zmetadata_path = os.path.join(self.out_ds_path, ZMETADATA)
        if not os.path.exists(zmetadata_path):
            shutil.copy(os.path.join(self.ref, ZMETADATA), zmetadata_path)

        with open(os.path.join(self.out_ds_path, ZMETADATA), 'r+') as f:
            metadata = json.load(f)
            f.seek(0)
            json.dump(json_metadata, f, indent=4, sort_keys=True)
            f.truncate()


    def handle_global_metadata(self):
        out_path = self.out_ds_path
        in_path = self.ref
        # .zgroup
        zgroup_path = os.path.join(out_path, ZGROUP)
        if not os.path.exists(zgroup_path):
            shutil.copy(os.path.join(in_path, ZGROUP), zgroup_path)

        # .zattrs
        zattrs_path = os.path.join(out_path, ZATTRS_FILE)
        if not os.path.exists(zattrs_path):
            shutil.copy(os.path.join(in_path, ZATTRS_FILE), zattrs_path)

        with open(zattrs_path, 'r+') as m:
            metadata = json.load(m)
            if 'sources' in metadata:
                metadata['sources'] += [os.path.basename(f) for f in self.paths]
            else:
                metadata['sources'] = [os.path.basename(f) for f in self.paths]
            m.seek(0)
            json.dump(metadata, m, indent=4, sort_keys=True)
            m.truncate()

        # .zmetadata
        self._handle_zmetadata()

class Union(object):
    def __init__(self, paths, output_path, metadata):
        self.paths = paths
        self.out_ds_path = output_path
        self.chunk_shape = (1024, 1024)
        self.metadata = metadata

    def _read_chunk(self, in_path, shape, dtype):
        with open(in_path, 'rb') as f:
            data = f.read()
            decompressed = blosc.decompress(data)
            x = np.frombuffer(decompressed, dtype=dtype).reshape(shape)
        return x

    def read_times(self, inputs):
        # Getting the time values from all inputs
        times = []
        time_axis = 0
        for path in inputs:
            dirname = os.path.basename(path)
            # print(dirname)
            if dirname != 'time':
                ref_path = os.path.join(path, 'time')
                (_, shape, _, dtype) = self.metadata.read_zarray(ref_path)
            else:
                ref_path = path
                (_, shape, _, dtype) = self.metadata.read_zarray(path)
            time_axis += shape[0]
            # Joining '0' too because it's the only chunk of time
            time_path = os.path.join(ref_path, '0')
            time_shape = shape[0]
            chunk_in = self._read_chunk(time_path, time_shape, dtype)
            times += [chunk_in]
        times = np.array(times)
        return (times, time_axis)

    def _handle_time(self):
        out_path = os.path.join(self.out_ds_path, 'time')
        (times, time_axis) = self.read_times(self.paths)

        if not self.metadata.is_append:
            os.mkdir(out_path)
            metadata_path = os.path.join(self.paths[0], 'time')
            (_, shape, cname, _) = self.metadata.read_zarray(metadata_path)
        else:
            metadata_path = out_path
            (_, shape, cname, _) = self.metadata.read_zarray(metadata_path)
            (times_exist, _) = self.read_times([out_path])
            times = np.concatenate((times, times_exist), axis=None)
            time_axis += shape[0]

        times = times.reshape(time_axis)
        print(f"Times before unique: {times}")
        time_np = np.unique(times, axis=0)
        print(f"Times: {time_np}")


        # TODO: verify check for time
        if np.any(np.diff(time_np.astype('int64')) <= 0):
            raise Exception('Cannot compute union due to time not being monotonically increasing.')


        time_shape = np.shape(time_np)
        if len(time_shape) == 1:
            time_np = np.array(time_np)
            time_axis = 1
        else:
            time_axis = time_shape[0]
        print(f"Lenght of times is: {np.shape(time_np)}")
        time_out = time_np.tobytes()

        chunk_path = os.path.join(out_path, '0')
        if os.path.exists(chunk_path):
            os.remove(chunk_path)

        # Writing the time values to the output ds
        with open(chunk_path, 'wb') as f:
            compressed = blosc.compress(time_out, cname=cname)
            f.write(compressed)
        # print(time_axis)

        # self.metadata.handle_time_metadata(metadata_path, out_path, time_axis, self.metadata.time_chunks)
        self.metadata.handle_time_metadata(metadata_path, out_path, np.shape(time_np)[0], self.metadata.time_chunks)

    def _group_inputs(self, var_name):
        groups = []
        group = []
        for p in self.paths:
            var_path = os.path.join(p, var_name)
            if os.path.exists(var_path):
                group.append(p)
                if len(group) == self.metadata.time_chunks:
                    groups += [group]
                    group = []
        if group:
            groups += [group]
        return groups

    def _process_chunks(self, groups, var, c, max_shape):
        z_axis = var.shape[0] + 1 if var.mode == CHUNK_APPEND else 0
        for group in groups:
            out = np.zeros(shape=(len(group), var.chunk[1], var.chunk[2]), dtype='int16')
            # TODO: change this so split's not necessary anymore
            (_, old_y, old_x) = (c.split('.')[0], c.split('.')[1], c.split('.')[2])
            new_filename = f"{z_axis}.{old_y}.{old_x}"
            for index, file in enumerate(group):
                # TODO: get from the metadata the fillvalue and check if the chunk is practically non-existing.
                chunk_path = os.path.join(file, var.name, c)
                if os.path.exists(chunk_path):
                    chunk_in = self._read_chunk(chunk_path, self.chunk_shape, var.dtype)
                else:
                    chunk_in = np.zeros(self.chunk_shape, dtype=var.dtype)
                out[index, :, :] = chunk_in
            out_bytes = out.tobytes()
            chunk_out = blosc.compress(out_bytes, typesize=2, cname=var.cname)
            chunk_out_path = os.path.join(var.out_path, new_filename)
            with open(chunk_out_path, 'wb') as f:
                f.write(chunk_out)
            z_axis += 1

        if z_axis >= max_shape:
            max_shape = z_axis

        return (var, max_shape)

    def _handle_chunks(self, variables):
        pool = multiprocessing.dummy.Pool()
        res = []
        for var in variables:
            groups = self._group_inputs(var.name)
            chunk_files = [f for f in os.listdir(var.in_path) if f[0] != '.']
            max_shape = 0
            for c in chunk_files:
                res.append(pool.apply_async(self._process_chunks, (groups, var, c, max_shape)))
        pool.close()

        for r in res:
            (var, max_shape) = r.get()
            var.z_axis = max_shape
            if var.mode != BAND_APPEND:
                self.metadata.handle_variable_metadata(var)

        pool.join()


    def compute(self):
        (_, variables_3D) = self.metadata.get_vars()
        # Handle variables
        self._handle_time()
        self._handle_chunks(variables_3D)
        self.metadata.handle_global_metadata()


parser = argparse.ArgumentParser(description="Union of multiple Zarr products")
parser.add_argument('--inputs', required=True, action='store', type=str, nargs="+")
parser.add_argument('--out', required=True)
parser.add_argument('--time', required=False, default=1)
args = parser.parse_args()

paths = args.inputs
output_path=args.out
time = int(args.time)

print(f"Inputs paths: {paths}")
print(f"Output path is {output_path}")

if not os.path.exists(output_path):
    try:
        os.mkdir(output_path)
    except OSError as exc:
        print(f"Can't create destination directory {output_path}")
    metadata = Metadata(paths, output_path, paths[0], False, time)
else:
    metadata = Metadata(paths, output_path, output_path, True, time)

union = Union(paths, output_path, metadata)
union.compute()
