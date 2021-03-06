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
package ro.cs.tao.datasource.remote.creodias.parsers;

import ro.cs.tao.utils.DateUtils;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class DateAdapter extends XmlAdapter<String, Date> {
    private static final List<DateFormat> formats = new ArrayList<DateFormat>() {{
        add(DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        add(DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        add(DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
        add(DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss.SSSS'Z'"));
        add(DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss.SSSSS'Z'"));
    }};

    @Override
    public Date unmarshal(String v) throws Exception {
        for (DateFormat format : formats) {
            try {
                return format.parse(v);
            } catch (ParseException ignored) {}
        }
        throw new ParseException("Unsupported date format", 0);
    }

    @Override
    public String marshal(Date v) throws Exception {
        return formats.get(0).format(v);
    }
}
