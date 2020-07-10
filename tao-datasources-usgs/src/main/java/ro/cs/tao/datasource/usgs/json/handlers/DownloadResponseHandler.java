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
package ro.cs.tao.datasource.usgs.json.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.usgs.json.NameValuePair;
import ro.cs.tao.datasource.usgs.json.responses.DownloadResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class DownloadResponseHandler implements JSonResponseHandler<NameValuePair> {
    @Override
    public List<NameValuePair> readValues(String content, AttributeFilter...filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        DownloadResponse response = mapper.readValue(content, DownloadResponse.class);
        final String error = response.getError();
        if (response.getData() == null) {
            throw new IOException(error != null ? error : "No data [error: null]");
        }
        return response.getData().stream().map(r -> {
            NameValuePair pair = new NameValuePair();
            pair.setName(r.getEntityId());
            pair.setValue(r.getUrl());
            return pair;
        }).collect(Collectors.toList());

    }
}
