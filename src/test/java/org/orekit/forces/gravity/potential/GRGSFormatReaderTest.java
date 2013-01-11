/* Copyright 2002-2013 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.forces.gravity.potential;


import java.io.IOException;
import java.text.ParseException;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;

public class GRGSFormatReaderTest {

    @Test
    public void testAdditionalColumn() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5-c1.txt", true));
        SphericalHarmonicsProvider provider = GravityFieldFactory.getSphericalHarmonicsProvider(5, 5);

        AbsoluteDate refDate = new AbsoluteDate("1997-01-01T12:00:00", TimeScalesFactory.getTT());
        Assert.assertEquals(refDate, provider.getReferenceDate());
        AbsoluteDate date = new AbsoluteDate("2011-05-01T01:02:03", TimeScalesFactory.getTT());
        Assert.assertEquals(date.durationFrom(refDate), provider.getOffset(date), Precision.SAFE_MIN);

        int maxUlps = 2;
        checkValue(provider.getUnnormalizedCnm( provider.getOffset(date), 3, 0), date, 3, 0,
                   1997, 1, 1, 0.95857491635129E-06, 0.28175700027753E-11, maxUlps);
        checkValue(provider.getUnnormalizedCnm( provider.getOffset(date), 5, 5), date, 5, 5,
                   1997, 1, 1, 0.17481512311600E-06, 0.0, maxUlps);
        checkValue(provider.getUnnormalizedSnm( provider.getOffset(date), 4, 0), date, 4, 0,
                   1997, 1, 1, 0, 0, maxUlps);
        checkValue(provider.getUnnormalizedSnm( provider.getOffset(date), 4, 4), date, 4, 4,
                   1997, 1, 1, 0.30882755318300E-06, 0, maxUlps);
        Assert.assertEquals(0.3986004415E+15 ,provider.getMu(),  0);
        Assert.assertEquals(0.6378136460E+07 ,provider.getAe(),  0);

    }

    @Test
    public void testRegular05c() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5_C1.dat", true));
        SphericalHarmonicsProvider provider = GravityFieldFactory.getSphericalHarmonicsProvider(5, 5);

        AbsoluteDate refDate = new AbsoluteDate("1997-01-01T12:00:00", TimeScalesFactory.getTT());
        Assert.assertEquals(refDate, provider.getReferenceDate());
        AbsoluteDate date = new AbsoluteDate("2011-05-01T01:02:03", TimeScalesFactory.getTT());
        Assert.assertEquals(date.durationFrom(refDate), provider.getOffset(date), Precision.SAFE_MIN);

        int maxUlps = 2;
        checkValue(provider.getUnnormalizedCnm( provider.getOffset(date), 3, 0), date, 3, 0,
                   1997, 1, 1, 0.95857491635129E-06, 0.28175700027753E-11, maxUlps);
        checkValue(provider.getUnnormalizedCnm( provider.getOffset(date), 5, 5), date, 5, 5,
                   1997, 1, 1, 0.17481512311600E-06, 0.0, maxUlps);
        checkValue(provider.getUnnormalizedSnm( provider.getOffset(date), 4, 0), date, 4, 0,
                   1997, 1, 1, 0, 0, maxUlps);
        checkValue(provider.getUnnormalizedSnm( provider.getOffset(date), 4, 4), date, 4, 4,
                   1997, 1, 1, 0.30882755318300E-06, 0, maxUlps);
        Assert.assertEquals(0.3986004415E+15 ,provider.getMu(),  0);
        Assert.assertEquals(0.6378136460E+07 ,provider.getAe(),  0);

    }

    @Test
    public void testReadLimits() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5_C1.dat", true));
        SphericalHarmonicsProvider provider = GravityFieldFactory.getSphericalHarmonicsProvider(3, 2);
        try {
            provider.getUnnormalizedCnm(0.0, 3, 3);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            // expected
        } catch (Exception e) {
            Assert.fail("wrong exception caught: " + e.getLocalizedMessage());
        }
        try {
            provider.getUnnormalizedCnm(0.0, 4, 2);
            Assert.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            // expected
        } catch (Exception e) {
            Assert.fail("wrong exception caught: " + e.getLocalizedMessage());
        }
        provider.getUnnormalizedCnm(0.0, 3, 2);
        Assert.assertEquals(3, provider.getMaxDegree());
        Assert.assertEquals(2, provider.getMaxOrder());
    }

    @Test(expected=OrekitException.class)
    public void testCorruptedFile1() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5_corrupted1.dat", false));
        GravityFieldFactory.getSphericalHarmonicsProvider(5, 5);
    }

    @Test(expected=OrekitException.class)
    public void testCorruptedFile2() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5_corrupted2.dat", false));
        GravityFieldFactory.getSphericalHarmonicsProvider(5, 5);
    }

    @Test(expected=OrekitException.class)
    public void testCorruptedFile3() throws IOException, ParseException, OrekitException {
        GravityFieldFactory.addPotentialCoefficientsReader(new GRGSFormatReader("grim5_corrupted3.dat", false));
        GravityFieldFactory.getSphericalHarmonicsProvider(5, 5);
    }

    @Before
    public void setUp() {
        Utils.setDataRoot("potential:regular-data");
    }

    private void checkValue(final double value,
                            final AbsoluteDate date, final int n, final int m,
                            final int refYear, final int refMonth, final int refDay,
                            final double constant, final double trend,
                            final int maxUlps) {
        double factor = GravityFieldFactory.getUnnormalizationFactors(n, m)[n][m];
        AbsoluteDate refDate = new AbsoluteDate(refYear, refMonth, refDay, 12, 0, 0, TimeScalesFactory.getTT());
        double dtYear = date.durationFrom(refDate) / Constants.JULIAN_YEAR;
        double normalized = factor * (constant + trend * dtYear);
        double epsilon = maxUlps * FastMath.ulp(normalized);
        Assert.assertEquals(normalized, value, epsilon);
    }

}
