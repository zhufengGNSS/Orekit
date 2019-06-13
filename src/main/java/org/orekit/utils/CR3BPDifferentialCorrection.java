/* Copyright 2002-2019 CS Systèmes d'Information
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

package org.orekit.utils;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.ode.events.Action;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.CR3BPSystem;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.XZPlaneCrossingDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.numerical.cr3bp.CR3BPForceModel;
import org.orekit.propagation.numerical.cr3bp.STMEquations;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;


/** Class implementing the differential correction method for Halo or Lyapunov Orbits.
 * It is not a simple differential correction, it uses higher order terms to be more accurate and meet orbits requirements.
 * Method used is expressed in the following article: Three-dimensional, periodic, Halo Orbits by Kathleen Connor Howell, Stanford University
 * @author Vincent Mouraux
 */
public class CR3BPDifferentialCorrection {

    /** Boolean return true if the propagated trajectory crosses the plane. */
    private static boolean cross;

    /** first guess PVCoordinates of the point to start differential correction. */
    private final PVCoordinates firstGuess;

    /** CR3BP System considered. */
    private final CR3BPSystem syst;

    /** orbitalPeriod Orbital Period of the required orbit. */
    private final double orbitalPeriod;

    /** Simple Constructor.
     * @param firstguess first guess PVCoordinates of the point to start differential correction
     * @param syst CR3BP System considered
     * @param orbitalPeriod Orbital Period of the required orbit
     */
    public CR3BPDifferentialCorrection(final PVCoordinates firstguess,
                                       final CR3BPSystem syst, final double orbitalPeriod) {
        this.firstGuess = firstguess;
        this.syst = syst;
        this.orbitalPeriod = orbitalPeriod;
    }

    /** Return the real starting point PVCoordinates on the Halo orbit after differential correction from a first guess.
     * @return pv Position-Velocity of the starting point on the Halo Orbit
     */
    public PVCoordinates compute() {

        // number of iteration
        double iter = 0;

        // Final velocity difference in X direction
        double dvxf;

        // Final velocity difference in Z direction
        double dvzf;

        final double[] param = new double[1];
        param[0] = syst.getMu();

        final RealMatrix A = MatrixUtils.createRealMatrix(2, 2);

        // Time settings
        final AbsoluteDate initialDate =
            new AbsoluteDate(1996, 06, 25, 0, 0, 00.000,
                             TimeScalesFactory.getUTC());

        final Frame rotatingFrame = syst.getRotatingFrame();

        // Initializing PVCoordinates with first guess
        PVCoordinates pv = firstGuess;

        // Maximum integration Time to cross XZ plane equals to one full orbit.
        final double integrationTime = orbitalPeriod;

        final double minStep = 1E-12;
        final double maxstep = 0.001;

        final double positionTolerance = 1E-9;
        final double velocityTolerance = 1E-9;
        final double massTolerance = 1.0e-6;
        final double[] vecAbsoluteTolerances = {positionTolerance, positionTolerance, positionTolerance, velocityTolerance, velocityTolerance, velocityTolerance, massTolerance };
        final double[] vecRelativeTolerances =
            new double[vecAbsoluteTolerances.length];

        final AdaptiveStepsizeIntegrator integrator =
            new DormandPrince853Integrator(minStep, maxstep,
                                             vecAbsoluteTolerances,
                                             vecRelativeTolerances);

        final double maxcheck = 10;
        final double threshold = 1E-10;

        final STMEquations stm = new STMEquations(syst);

        final EventDetector XZPlaneCrossing =
            new XZPlaneCrossingDetector(maxcheck, threshold)
                .withHandler(new planeCrossingHandler());

        do {
            final AbsolutePVCoordinates initialAbsPV =
                new AbsolutePVCoordinates(rotatingFrame, initialDate, pv);

            final SpacecraftState initialState =
                new SpacecraftState(initialAbsPV);

            final SpacecraftState augmentedInitialState =
                stm.setInitialPhi(initialState);

            cross = false;

            final NumericalPropagator propagator =
                new NumericalPropagator(integrator);
            propagator.setOrbitType(null);
            propagator.setIgnoreCentralAttraction(true);
            propagator.addForceModel(new CR3BPForceModel(syst));
            propagator.addAdditionalEquations(stm);
            propagator.addEventDetector(XZPlaneCrossing);
            propagator.setInitialState(augmentedInitialState);

            final SpacecraftState finalState =
                propagator.propagate(initialDate.shiftedBy(integrationTime));
            final RealMatrix phi = stm.getStateTransitionMatrix(finalState);

            dvxf = -finalState.getPVCoordinates().getVelocity().getX();
            final double vy = finalState.getPVCoordinates().getVelocity().getY();
            dvzf = -finalState.getPVCoordinates().getVelocity().getZ();

            final Vector3D acc = new CR3BPForceModel(syst).acceleration(finalState, param);
            final double accx = acc.getX();
            final double accz = acc.getZ();

            final double a11 =
                phi.getEntry(3, 0) - accx * phi.getEntry(1, 0) / vy;
            final double a12 =
                phi.getEntry(3, 4) - accx * phi.getEntry(1, 4) / vy;
            final double a21 =
                phi.getEntry(5, 0) - accz * phi.getEntry(1, 0) / vy;
            final double a22 =
                phi.getEntry(5, 4) - accz * phi.getEntry(1, 4) / vy;

            A.setEntry(0, 0, a11);
            A.setEntry(0, 1, a12);
            A.setEntry(1, 0, a21);
            A.setEntry(1, 1, a22);

            final double Mdet =
                A.getEntry(0, 0) * A.getEntry(1, 1) -
                                A.getEntry(1, 0) * A.getEntry(0, 1);

            final double deltax0 =
                (A.getEntry(1, 1) * dvxf - A.getEntry(0, 1) * dvzf) / Mdet; // dx0
            final double deltavy0 =
                (-A.getEntry(1, 0) * dvxf + A.getEntry(0, 0) * dvzf) / Mdet; // dvy0

            final double newx = pv.getPosition().getX() + deltax0;
            final double newvy = pv.getVelocity().getY() + deltavy0;

            pv =
                new PVCoordinates(new Vector3D(newx, pv.getPosition().getY(),
                                               pv.getPosition().getZ()),
                                  new Vector3D(pv.getVelocity().getX(), newvy,
                                               pv.getVelocity().getZ()));

            ++iter;
        } while ((FastMath.abs(dvxf) > 1E-8 || FastMath.abs(dvzf) > 1E-8) &
                 iter < 5);

        if (cross) {
            return pv;
        } else {
            System.out
                .println("Your orbit does not cross XZ plane, trajectory wont result into an Halo Orbit");
            return pv;
        }
    }

    /** Static class for event detection.
     */
    private static class planeCrossingHandler
        implements
        EventHandler<XZPlaneCrossingDetector> {

        /** {@inheritDoc} */
        public Action eventOccurred(final SpacecraftState s,
                                    final XZPlaneCrossingDetector detector,
                                    final boolean increasing) {
            cross = true;
            return Action.STOP;
        }
    }
}
