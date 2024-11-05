package ro.cs.tao.tests;/*
 *
 *  * Copyright (C) 2018 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *  *
 *
 */

/*
 *
 *  * Copyright (C) 2018 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *  *
 *
 */

import org.junit.Test;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.tests.DataSourceTest;

/**
 * @author Cosmin Cara
 */
public class DASTest extends DataSourceTest {

    public DASTest(Class dsClass) {
        super(dsClass);
    }

    @Test
    public void testSentinel1SLC() {
        testSensor("Sentinel1", "SLC", false);
    }

    @Test
    public void testSentinel1GRDCOG() {
        testSensor("Sentinel1", "GRD-COG", false);
    }

    @Test
    public void testSentinel1OrbitFiles() {
        testSensor("Sentinel1", "AUX_POEORB", false);
    }

    @Test
    public void testS2L1C() {
        testSensor("Sentinel2", "L1C", false);
    }

    @Test
    public void testS2L2A() {
        testSensor("Sentinel2", "L2A", false);
    }

    @Test
    public void testS3LST() {
        QueryParameter<String> param = new QueryParameter<>(String.class, "productSize", "FRAME");
        testSensor("Sentinel3", "SL_2_LST___", false, param);
    }

    @Test
    public void testS5PL2CO() {
        QueryParameter<String> param = new QueryParameter<>(String.class, "timeliness", "NRTI");
        testSensor("Sentinel5P", "L2__CO____", false, param);
    }

    @Test
    public void testS5PL2NO2() {
        QueryParameter<String> param = new QueryParameter<>(String.class, "timeliness", "NRTI");
        testSensor("Sentinel5P", "L2__NO2___", false, param);
    }
}
