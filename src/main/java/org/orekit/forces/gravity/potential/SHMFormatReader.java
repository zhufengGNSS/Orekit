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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.DateComponents;
import org.orekit.utils.Constants;

/** Reader for the SHM gravity field format.
 *
 * <p> This format was used to describe the gravity field of EIGEN models
 * published by the GFZ Potsdam up to 2003. It was then replaced by
 * {@link ICGEMFormatReader ICGEM format}. The SHM format is described in
 * <a href="http://www.gfz-potsdam.de/grace/results/"> Potsdam university
 * website</a>.
 *
 * <p> The proper way to use this class is to call the {@link GravityFieldFactory}
 *  which will determine which reader to use with the selected gravity field file.</p>
 *
 * @see GravityFieldFactory
 * @author Fabien Maussion
 */
public class SHMFormatReader extends PotentialCoefficientsReader {

    /** First field labels. */
    private static final String GRCOEF = "GRCOEF";

    /** Second field labels. */
    private static final String GRCOF2 = "GRCOF2";

    /** Drift coefficients labels. */
    private static final String GRDOTA = "GRDOTA";

    /** Reference date. */
    private DateComponents referenceDate;

    /** Secular drift of the cosine coefficients. */
    private final List<List<Double>> cDot;

    /** Secular drift of the sine coefficients. */
    private final List<List<Double>> sDot;

    /** Simple constructor.
     * @param supportedNames regular expression for supported files names
     * @param missingCoefficientsAllowed if true, allows missing coefficients in the input data
     */
    public SHMFormatReader(final String supportedNames, final boolean missingCoefficientsAllowed) {
        super(supportedNames, missingCoefficientsAllowed);
        referenceDate = null;
        cDot = new ArrayList<List<Double>>();
        sDot = new ArrayList<List<Double>>();
    }

    /** {@inheritDoc} */
    public void loadData(final InputStream input, final String name)
        throws IOException, ParseException, OrekitException {

        // reset the indicator before loading any data
        setReadComplete(false);
        referenceDate = null;
        cDot.clear();
        sDot.clear();

        final BufferedReader r = new BufferedReader(new InputStreamReader(input));
        boolean okEarth            = false;
        boolean okSHM              = false;
        boolean okCoeffs           = false;
        double[][] c               = null;
        double[][] s               = null;
        String line = r.readLine();
        if ((line != null) &&
            "FIRST ".equals(line.substring(0, 6)) &&
            "SHM    ".equals(line.substring(49, 56))) {
            for (line = r.readLine(); line != null; line = r.readLine()) {
                if (line.length() >= 6) {
                    final String[] tab = line.split("\\s+");

                    // read the earth values
                    if ("EARTH".equals(tab[0])) {
                        setMu(Double.parseDouble(tab[1].replace('D', 'E')));
                        setAe(Double.parseDouble(tab[2].replace('D', 'E')));
                        okEarth = true;
                    }

                    // initialize the arrays
                    if ("SHM".equals(tab[0])) {

                        final int degree = FastMath.min(getMaxParseDegree(), Integer.parseInt(tab[1]));
                        final int order  = FastMath.min(getMaxParseOrder(), degree);
                        c = buildTriangularArray(degree, order, missingCoefficientsAllowed() ? 0.0 : Double.NaN);
                        s = buildTriangularArray(degree, order, missingCoefficientsAllowed() ? 0.0 : Double.NaN);
                        okSHM = true;
                    }

                    // fill the arrays
                    if (GRCOEF.equals(line.substring(0, 6)) || GRCOF2.equals(tab[0]) || GRDOTA.equals(tab[0])) {
                        final int i = Integer.parseInt(tab[1]);
                        final int j = Integer.parseInt(tab[2]);
                        if (i < c.length && j < c[i].length) {
                            if (GRDOTA.equals(tab[0])) {

                                // store the secular drift coefficients
                                extendListOfLists(cDot, i, j, 0.0);
                                extendListOfLists(sDot, i, j, 0.0);
                                parseCoefficient(tab[3], cDot, i, j, "Cdot", name);
                                parseCoefficient(tab[4], sDot, i, j, "Sdot", name);

                                // check the reference date (format yyyymmdd)
                                final DateComponents localRef = new DateComponents(Integer.parseInt(tab[7].substring(0, 4)),
                                                                                   Integer.parseInt(tab[7].substring(4, 6)),
                                                                                   Integer.parseInt(tab[7].substring(6, 8)));
                                if (referenceDate == null) {
                                    // first reference found, store it
                                    referenceDate = localRef;
                                } else if (!referenceDate.equals(localRef)) {
                                    throw new OrekitException(OrekitMessages.SEVERAL_REFERENCE_DATES_IN_GRAVITY_FIELD,
                                                              referenceDate, localRef, name);
                                }

                            } else {

                                // store the constant coefficients
                                parseCoefficient(tab[3], c, i, j, "C", name);
                                parseCoefficient(tab[4], s, i, j, "S", name);
                                okCoeffs = true;

                            }
                        }
                    }

                }
            }
        }

        if (missingCoefficientsAllowed() && c.length > 0 && c[0].length > 0) {
            // ensure at least the (0, 0) element is properly set
            if (Precision.equals(c[0][0], 0.0, 1)) {
                c[0][0] = 1.0;
            }
        }

        if (!(okEarth && okSHM && okCoeffs)) {
            String loaderName = getClass().getName();
            loaderName = loaderName.substring(loaderName.lastIndexOf('.') + 1);
            throw new OrekitException(OrekitMessages.UNEXPECTED_FILE_FORMAT_ERROR_FOR_LOADER,
                                      name, loaderName);
        }

        setNormalizedC(c, name);
        setNormalizedS(s, name);
        setReadComplete(true);

    }

    /** Get a provider for read spherical harmonics coefficients.
     * <p>
     * SHM fields do include time-dependent parts which are taken into account
     * in the returned provider.
     * </p>
     * @param degree maximal degree
     * @param order maximal order
     * @return a new provider
     * @exception OrekitException if the requested maximal degree or order exceeds the
     * available degree or order or if no gravity field has read yet
     */
    public SphericalHarmonicsProvider getProvider(int degree, int order)
        throws OrekitException {

        final ConstantSphericalHarmonics constant = getConstantProvider(degree, order);
        if (cDot.isEmpty()) {
            // there are no time-dependent coefficients
            return constant;
        }

        // copy the time-dependent coefficients
        final double[][] cArray = toArray(cDot);
        final double[][] sArray = toArray(sDot);
        final double[][] factors = GravityFieldFactory.getUnnormalizationFactors(cArray.length, cArray.length);
        for (int i = 0; i < cArray.length; ++i) {
            for (int j = 0; j < cArray[i].length; ++j) {
                final double f = factors[i][j] / Constants.JULIAN_YEAR;
                cArray[i][j] = f * cDot.get(i).get(j);
                sArray[i][j] = f * sDot.get(i).get(j);
            }
        }

        return new SecularTrendSphericalHarmonics(constant, referenceDate, cArray, sArray);

    }

}
