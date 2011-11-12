/* Copyright 2002-2011 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
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
package org.orekit.forces.maneuvers;


import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.apache.commons.math.ode.nonstiff.DormandPrince853Integrator;
import org.apache.commons.math.util.FastMath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.orbits.CircularOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class SmallManeuverAnalyticalModelTest {

    @Test
    public void testLowEarthOrbit() throws OrekitException {

        Orbit leo = new CircularOrbit(7200000.0, -1.0e-5, 2.0e-4,
                                      FastMath.toRadians(98.0),
                                      FastMath.toRadians(123.456),
                                      0.0, PositionAngle.MEAN,
                                      FramesFactory.getEME2000(),
                                      new AbsoluteDate(new DateComponents(2004, 01, 01),
                                                       new TimeComponents(23, 30, 00.000),
                                                       TimeScalesFactory.getUTC()),
                                      Constants.EIGEN5C_EARTH_MU);
        double mass     = 5600.0;
        AbsoluteDate t0 = leo.getDate().shiftedBy(1000.0);
        Vector3D dV     = new Vector3D(-0.01, 0.02, 0.03);
        double f        = 20.0;
        double isp      = 315.0;
        BoundedPropagator withoutManeuver = getEphemeris(leo, mass, t0, Vector3D.ZERO, f, isp);
        BoundedPropagator withManeuver    = getEphemeris(leo, mass, t0, dV, f, isp);
        SmallManeuverAnalyticalModel model =
                new SmallManeuverAnalyticalModel(withoutManeuver.propagate(t0), dV, isp);

        for (AbsoluteDate t = withoutManeuver.getMinDate();
             t.compareTo(withoutManeuver.getMaxDate()) < 0;
             t = t.shiftedBy(60.0)) {
            PVCoordinates pvWithout = withoutManeuver.getPVCoordinates(t, leo.getFrame());
            PVCoordinates pvWith    = withManeuver.getPVCoordinates(t, leo.getFrame());
            PVCoordinates pvModel   = model.applyManeuver(withoutManeuver.propagate(t)).getPVCoordinates(leo.getFrame());
            double nominalDeltaP    = new PVCoordinates(pvWith, pvWithout).getPosition().getNorm();
            double modelError       = new PVCoordinates(pvWith, pvModel).getPosition().getNorm();
            if (t.compareTo(t0) < 0) {
                // before maneuver, all positions should be equal
                Assert.assertEquals(0, nominalDeltaP, 1.0e-10);
                Assert.assertEquals(0, modelError,    1.0e-10);
            } else {
                // after maneuver, model error should be less than 0.8m,
                // despite nominal deltaP exceeds 1 kilometer after less than 3 orbits
                if (t.durationFrom(t0) > 0.1 * leo.getKeplerianPeriod()) {
                    Assert.assertTrue(modelError < 0.009 * nominalDeltaP);
                }
                Assert.assertTrue(modelError < 0.8);
            }
        }

    }

    @Test
    public void testEccentricOrbit() throws OrekitException {

        Orbit heo = new KeplerianOrbit(90000000.0, 0.92, FastMath.toRadians(98.0),
                                       FastMath.toRadians(12.3456),
                                       FastMath.toRadians(123.456),
                                       FastMath.toRadians(1.23456), PositionAngle.MEAN,
                                       FramesFactory.getEME2000(),
                                       new AbsoluteDate(new DateComponents(2004, 01, 01),
                                                        new TimeComponents(23, 30, 00.000),
                                                        TimeScalesFactory.getUTC()),
                                                        Constants.EIGEN5C_EARTH_MU);
        double mass     = 5600.0;
        AbsoluteDate t0 = heo.getDate().shiftedBy(1000.0);
        Vector3D dV     = new Vector3D(-0.01, 0.02, 0.03);
        double f        = 20.0;
        double isp      = 315.0;
        BoundedPropagator withoutManeuver = getEphemeris(heo, mass, t0, Vector3D.ZERO, f, isp);
        BoundedPropagator withManeuver    = getEphemeris(heo, mass, t0, dV, f, isp);
        SmallManeuverAnalyticalModel model =
                new SmallManeuverAnalyticalModel(withoutManeuver.propagate(t0), dV, isp);

        for (AbsoluteDate t = withoutManeuver.getMinDate();
             t.compareTo(withoutManeuver.getMaxDate()) < 0;
             t = t.shiftedBy(600.0)) {
            PVCoordinates pvWithout = withoutManeuver.getPVCoordinates(t, heo.getFrame());
            PVCoordinates pvWith    = withManeuver.getPVCoordinates(t, heo.getFrame());
            PVCoordinates pvModel   = model.applyManeuver(withoutManeuver.propagate(t)).getPVCoordinates(heo.getFrame());
            double nominalDeltaP    = new PVCoordinates(pvWith, pvWithout).getPosition().getNorm();
            double modelError       = new PVCoordinates(pvWith, pvModel).getPosition().getNorm();
            if (t.compareTo(t0) < 0) {
                // before maneuver, all positions should be equal
                Assert.assertEquals(0, nominalDeltaP, 1.0e-10);
                Assert.assertEquals(0, modelError,    1.0e-10);
            } else {
                // after maneuver, model error should be less than 1700m,
                // despite nominal deltaP exceeds 300 kilometers at perigee, after 3 orbits
                if (t.durationFrom(t0) > 0.01 * heo.getKeplerianPeriod()) {
                    Assert.assertTrue(modelError < 0.005 * nominalDeltaP);
                }
                Assert.assertTrue(modelError < 1700);
            }
        }

    }

    private BoundedPropagator getEphemeris(final Orbit orbit, final double mass,
                                           final AbsoluteDate t0, final Vector3D dV,
                                           final double f, final double isp)
        throws OrekitException {

        final AttitudeProvider law = new LofOffset(orbit.getFrame(), LOFType.LVLH);
        final SpacecraftState initialState =
            new SpacecraftState(orbit, law.getAttitude(orbit, orbit.getDate(), orbit.getFrame()), mass);


        // set up numerical propagator
        final double dP = 1.0;
        double[][] tolerances = NumericalPropagator.tolerances(dP, orbit, orbit.getType());
        AdaptiveStepsizeIntegrator integrator =
            new DormandPrince853Integrator(0.001, 1000, tolerances[0], tolerances[1]);
        integrator.setInitialStepSize(orbit.getKeplerianPeriod() / 100.0);
        final NumericalPropagator propagator = new NumericalPropagator(integrator);
        propagator.setInitialState(initialState);
        propagator.setAttitudeProvider(law);

        if (dV.getNorm() > 1.0e-6) {
            // set up maneuver
            final double vExhaust = Constants.G0_STANDARD_GRAVITY * isp;
            final double dt = -(mass * vExhaust / f) * FastMath.expm1(-dV.getNorm() / vExhaust);
            final ConstantThrustManeuver maneuver =
                    new ConstantThrustManeuver(t0, dt , f, isp, dV.normalize());
            propagator.addForceModel(maneuver);
        }

        propagator.setEphemerisMode();
        propagator.propagate(t0.shiftedBy(5 * orbit.getKeplerianPeriod()));
        return propagator.getGeneratedEphemeris();

    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data");
    }

}
