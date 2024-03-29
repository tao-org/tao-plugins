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
package ro.cs.tao.products.sentinels;

import ro.cs.tao.utils.DateUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class L1CProductHelper extends Sentinel2ProductHelper {

    static final Pattern ProductV13 = Pattern.compile("(S2[A-B])_(OPER)_(PRD)_(MSIL1C)_(PDMC)_(\\d{8}T\\d{6})_R(\\d{3})_V(\\d{8}T\\d{6})_(\\d{8}T\\d{6})(?:.SAFE)?");
    static final Pattern ProductV14 = Pattern.compile("(S2[A-B])_(MSIL1C)_(\\d{8}T\\d{6})_(N\\d{4})_R(\\d{3})_T(\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");
    private static final Pattern TileV13 = Pattern.compile("(S2[A-B])_(OPER)_(MSI)_(L1C)_(TL)_(\\w{3})__(\\d{8}T\\d{6})_(A\\d{6})_T(\\d{2}\\w{3})_(N\\d{2}.\\d{2})");

    private boolean oldFormat;

    public L1CProductHelper() {
        super();
    }

    L1CProductHelper(String name) {
        super(name);
        this.version = this.oldFormat ? PSD_13 : PSD_14;
    }

    @Override
    public L1CProductHelper duplicate() {
        return new L1CProductHelper(getName());
    }

    @Override
    public String getVersion() {
        if (this.version == null) {
            this.version = this.oldFormat ? PSD_13 : PSD_14;
        }
        return this.version;
    }

    @Override
    public String getOrbit() {
        return this.oldFormat ? getTokens(ProductV13)[6] : getTokens(ProductV14)[4];
    }

    @Override
    public String getSensingDate() {
        return this.oldFormat ? getTokens(ProductV13)[7] : getTokens(ProductV14)[2];
    }

    @Override
    public String getProcessingDate() {
        return this.oldFormat ? getTokens(ProductV13)[5] : getTokens(ProductV14)[6];
    }

    @Override
    public String getProductRelativePath() {
        String year, day, month;
        if (this.oldFormat) {
            String[] tokens = getTokens(ProductV13);
            String dateToken = tokens[7];
            year = dateToken.substring(1, 5);
            month = String.valueOf(Integer.parseInt(dateToken.substring(5, 7)));
            day = String.valueOf(Integer.parseInt(dateToken.substring(7, 9)));
        } else {
            String[] tokens = getTokens(ProductV14);
            String dateToken = tokens[2];
            year = dateToken.substring(0, 4);
            month = String.valueOf(Integer.parseInt(dateToken.substring(4, 6)));
            day = String.valueOf(Integer.parseInt(dateToken.substring(6, 8)));
        }
        return year + URL_SEPARATOR
                + month + URL_SEPARATOR
                + day + URL_SEPARATOR
                + this.name + URL_SEPARATOR;
    }

    @Override
    public String getTileIdentifier() {
        String tileId = null;
        if (!this.oldFormat) {
            tileId = getTokens(ProductV14)[5];
        }
        return tileId;
    }

    @Override
    public String getMetadataFileName() {
        String metaName;
        String[] tokens;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        if (this.oldFormat) {
            tokens = getTokens(ProductV13, prodName, new HashMap<Integer, String>() {{
                put(2, "MTD"); put(3, "SAFL1C");
            }});
            metaName = String.join("_", tokens) + ".xml";
        } else {
            metaName = "MTD_MSIL1C.xml";
        }
        return metaName;
    }

    @Override
    public String getDatastripMetadataFileName(String datastripIdentifier) {
        String name;
        if (this.oldFormat) {
            name = datastripIdentifier.substring(0, datastripIdentifier.lastIndexOf("_")) + ".xml";
        } else {
            name = "MTD_DS.xml";
        }
        return name;
    }

    @Override
    public String getDatastripFolder(String datastripIdentifier) {
        String folder;
        if (this.oldFormat) {
            folder = datastripIdentifier;
        } else {
            folder = datastripIdentifier.substring(17, 57);
        }
        return folder;
    }

    @Override
    public String getGranuleFolder(String datastripIdentifier, String granuleIdentifier) {
        String folder;
        if (this.oldFormat) {
            folder = granuleIdentifier;
        } else {
            folder = granuleIdentifier.substring(13, 16) + "_" +
                    granuleIdentifier.substring(49, 55) + "_" +
                    granuleIdentifier.substring(41, 48) + "_" +
                    datastripIdentifier.substring(42, 57);
        }
        return folder;
    }

    @Override
    public String getGranuleMetadataFileName(String granuleIdentifier) {
        String metaName;
        if (this.oldFormat) {
            String tokens[] = getTokens(TileV13, granuleIdentifier, new HashMap<Integer, String>() {{
                put(2, "MTD"); put(9, "");
            }});
            metaName = String.join("_", Arrays.copyOfRange(tokens, 0, 6)) + "__" + String.join("_", Arrays.copyOfRange(tokens, 6, 9)) + ".xml";
        } else {
            metaName = "MTD_TL.xml";
        }
        return metaName;
    }

    @Override
    public String getBandFileName(String granuleIdentifier, String band) {
        String fileName;
        String[] tokens;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        if (this.oldFormat) {
            tokens = getTokens(TileV13, granuleIdentifier, null);
            fileName = String.join("_", Arrays.copyOfRange(tokens, 0, 6)) + "__" + String.join("_", Arrays.copyOfRange(tokens, 6, 9)) + "_" + band;
        } else {
            tokens = getTokens(ProductV14, prodName, null);
            fileName = "T" + tokens[5] + "_" + tokens[2] + "_" + band;
        }
        return fileName;
    }

    @Override
    public String getEcmWftFileName(String granuleIdentifier) {
        String fileName;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        if (this.oldFormat) {
            String[] granuleTokens = getTokens(TileV13, granuleIdentifier, null);
            String[] productTokens = getTokens(ProductV13, prodName, null);
            DateTimeFormatter dateFormat = DateUtils.getFormatterAtUTC("yyyyMMdd'T'HHmmss");
            String dateStart = "", dateEnd = "";
            try {
                LocalDateTime date1 = LocalDateTime.parse(productTokens[7].substring(1), dateFormat);
                date1 = LocalDateTime.of(date1.getYear(), date1.getMonth(), date1.getDayOfMonth(), date1.getHour(), 0, 0);
                dateStart = dateFormat.format(date1);
                dateEnd = dateFormat.format(date1.plusHours(12));
            } catch (DateTimeParseException e) {
                Logger.getLogger(L1CProductHelper.class.getName()).severe(e.getMessage());
            }
            fileName = String.join("_", productTokens[0], productTokens[1], "AUX", "ECMWFT", granuleTokens[5], "", granuleTokens[6], dateStart, dateEnd);
        } else {
            fileName = "AUX_ECMWFT";
        }
        return fileName;
    }

    @Override
    protected boolean verifyProductName(String name) {
        this.oldFormat = ProductV13.matcher(name).matches();
        return this.oldFormat || ProductV14.matcher(name).matches();
    }
}
