#!/usr/bin/env python

import argparse
import numpy as np
import os
import json
import blosc
import shutil
import itertools

ZARRAY_FILE = '.zarray'
ZATTRS_FILE = '.zattrs'
ZGROUP = '.zgroup'
ZMETADATA = '.zmetadata'

# ZARRAY attributes
CHUNKS = 'chunks'
COMPRESSOR = 'compressor'
SHAPE = 'shape'

class Variable(object):
    def __init__(self, path, name, shape, chunk, cname):
        self.path = path
        self.name = name
        self.shape = shape
        self.chunk = chunk
        self.cname = cname

    def __str__(self):
        return self.name + " " + self.path

class VariableCollection(object):
    def __init__(self):
        self.variables = []

    def add(self, variable):
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
                    (chunk, shape, cname) = self.read_zarray(os.path.join(path, f))
                    variable = Variable(os.path.join(path, f), f, shape, chunk, cname)
                    self.variable_collection.add(variable)
        return self.variable_collection.variables


    def read_zarray(self, path):
        metadata_file = os.path.join(path, ZARRAY_FILE)
        cname = None
        with open(metadata_file, 'r') as f:
            metadata = json.load(f)
            if COMPRESSOR in metadata and metadata[COMPRESSOR]:
                cname = metadata[COMPRESSOR]['cname']
        return (metadata[CHUNKS], metadata[SHAPE], cname)

    def handle_time_metadata(self, in_path, out_path, shape, time_chunk, is_write):
        if is_write:
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


    def handle_variable_metadata(self, variable_path, variable_name, shape):
        # Handle zattrs
        if not self.is_append:
            shutil.copy(os.path.join(variable_path, ZATTRS_FILE), os.path.join(self.out_ds_path, variable_name, ZATTRS_FILE))

        # Handle .zarray
        with open(os.path.join(variable_path, ZARRAY_FILE), 'r') as f:
            metadata = json.load(f)

        out_path = os.path.join(self.out_ds_path, variable_name, ZARRAY_FILE)
        if not self.is_append:
            shutil.copy(os.path.join(variable_path, ZARRAY_FILE), out_path)

        with open(out_path, 'r+') as f:
            meta = json.load(f)
            metadata[SHAPE][0] = int(shape)
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

    def _get_vars(self, ref_ds):
        return [f for f in os.listdir(ref_ds) if f[0] != '.' and f != 'time']

    def _read_chunk(self, in_path, shape):
        with open(in_path, 'rb') as f:
            data = f.read()
            decompressed = blosc.decompress(data)
            x = np.frombuffer(decompressed, dtype='i2').reshape(shape)
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
                (_, shape, cname) = self.metadata.read_zarray(ref_path)
            else:
                ref_path = path
                (_, shape, cname) = self.metadata.read_zarray(path)
            time_axis += shape[0]
            # Joining '0' too because it's the only chunk of time
            time_path = os.path.join(ref_path, '0')
            time_shape = (shape[0]*4, )
            chunk_in = self._read_chunk(time_path, time_shape)
            times += [chunk_in]
        times = np.array(times)
        return (times, time_axis)

    def _handle_time(self):
        out_path = os.path.join(self.out_ds_path, 'time')
        (times, time_axis) = self.read_times(self.paths)

        is_write = False
        if not os.path.exists(out_path):
            os.mkdir(out_path)
            metadata_path = os.path.join(self.paths[0], 'time')
            (_, shape, cname) = self.metadata.read_zarray(metadata_path)
            is_write = True
        else:
            metadata_path = out_path
            (_, shape, cname) = self.metadata.read_zarray(metadata_path)
            (times_exist, _) = self.read_times([out_path])
            times = np.concatenate((times, times_exist), axis=None)
            time_axis += shape[0]

        times = times.reshape(time_axis, 4)
        print(f"Times before unique: {times}")
        time_np = np.unique(times, axis=0)
        print(f"Times: {time_np}")
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
            compressed = blosc.compress(time_out, typesize=2, cname=cname)
            f.write(compressed)

        self.metadata.handle_time_metadata(metadata_path, out_path, time_axis, self.metadata.time_chunks, is_write)

    def _group_inputs(self, var_name):
        # n = self.metadata.time_chunks
        groups = []
        group = []
        for p in self.paths:
            var_path = os.path.join(p, var_name)
            if os.path.exists(var_path):
                group.append(p)
                if len(group) == self.metadata.time_chunks:
                    groups += [group]
                    group = []
        if len(group) > 0:
            groups += [group]
        # chunks = [self.paths[x: x+n] for x in range(0, len(self.paths), n)]
        print(groups)
        return groups


    def _handle_chunks(self, variables):
        for var in variables:
            var_name = var.name
            groups = self._group_inputs(var_name)
            # var_in_path = os.path.join(self.metadata.ref, var_name)
            var_in_path = var.path
            print(var_in_path)
            (chunks, shape, cname) = (var.chunk, var.shape, var.cname)
            var_out_path = os.path.join(self.out_ds_path, var_name)
            if not os.path.exists(var_out_path):
                os.mkdir(var_out_path)

            # (chunks, shape, cname) = self.metadata.read_zarray(var_in_path)
            chunk_files = [f for f in os.listdir(var_in_path) if f[0] != '.']
            max_shape = 0
            for c in chunk_files:
                if self.metadata.is_append:
                    z_axis = shape[0] + 1
                else:
                    z_axis = 0
                for group in groups:
                    out = np.zeros(shape=(len(group), chunks[1], chunks[2]), dtype='int16')
                    (old_z, old_y, old_x) = (c.split('.')[0], c.split('.')[1], c.split('.')[2])
                    new_filename = f"{z_axis}.{old_y}.{old_x}"
                    for index, file in enumerate(group):
                        chunk_path = os.path.join(file, var_name, c)
                        if os.path.exists(chunk_path):
                            chunk_in = self._read_chunk(chunk_path, self.chunk_shape)
                            out[index, :, :] = chunk_in
                    out_bytes = out.tobytes()
                    chunk_out = blosc.compress(out_bytes, typesize=2, cname=cname)
                    chunk_out_path = os.path.join(var_out_path, new_filename)
                    with open(chunk_out_path, 'wb') as f:
                        f.write(chunk_out)
                    z_axis += 1
                if z_axis >= max_shape:
                    max_shape = z_axis

                self.metadata.handle_variable_metadata(var_in_path, var_name, max_shape)



    def _handle_variables(self, variables):
        if self.metadata.is_append:
            return

        for var in variables:
            path = os.path.join(self.metadata.ref, var.name)
            shutil.copytree(path, os.path.join(self.out_ds_path, var.name))


    def compute(self):
        vars_list = self.metadata.get_vars()
        # (variables, variables_3D) = self.metadata.get_vars()
        variables = [v for v in vars_list if len(v.shape) != 3]
        variables_3D = [v for v in vars_list if len(v.shape) == 3]

        # Handle variables
        self._handle_time()
        self._handle_variables(variables)
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


if not os.path.exists(output_path):
    os.mkdir(output_path)
    metadata = Metadata(paths, output_path, paths[0], False, time)
else:
    metadata = Metadata(paths, output_path, output_path, True, time)

union = Union(paths, output_path, metadata)
union.compute()
