package org.orekit.propagation.semianalytical.dsst.dsstforcemodel;

import java.util.TreeMap;

import org.apache.commons.math.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math.util.FastMath;
import org.orekit.bodies.CelestialBody;
import org.orekit.errors.OrekitException;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.semianalytical.dsst.DSSTPropagator;
import org.orekit.propagation.semianalytical.dsst.dsstforcemodel.CoefficientFactory.NSKey;
import org.orekit.time.AbsoluteDate;

/** Third body attraction contribution for {@link DSSTPropagator}.
 *
 *  @author Romain Di Costanzo
 *  @author Pascal Parraud
 */
public class DSSTThirdBody implements DSSTForceModel {

    /** Propagation orbit type. */
    private static final OrbitType ORBIT_TYPE = OrbitType.EQUINOCTIAL;

    /** Position angle type. */
    private static final PositionAngle ANGLE_TYPE = PositionAngle.MEAN;

    /** Default convergence parameter used in Hansen coefficient generation. */
    private static final double DEFAULT_EPSILON = 1.e-4;

    /** Default N order for summation. */
    private static final int DEFAULT_ORDER = 10;

    /** The 3rd body to consider. */
    private final CelestialBody body;

    /** Standard gravitational parameter &mu; for the body in m<sup>3</sup>/s<sup>2</sup>. */
    private final double gm;

    /** Convergence parameter used in Hansen coefficient generation. */
    private final double epsilon;

    /** N order for summation. */
    private final int order;

    /** V<sub>ns</sub> coefficients. */
    private TreeMap<NSKey, Double> Vns;

    // Equinoctial elements (according to DSST notation)
    /** a */
    private double a;
    /** ex */
    private double k;
    /** ey */
    private double h;
    /** hx */
    private double q;
    /** hy */
    private double p;

    /** Distance from center of mass of the central body to the 3rd body */
    private double R3;

    // Useful equinoctial coefficients
    /** A = sqrt(&mu; * a) */
    private double A;
    /** B = sqrt(1 - h<sup>2</sup> - k<sup>2</sup>) */
    private double B;
    /** C = 1 + p<sup>2</sup> + q<sup>2</sup> */
    private double C;

    // Direction cosines of the symmetry axis
    /** &alpha; */
    private double alpha;
    /** &beta; */
    private double beta;
    /** &gamma; */
    private double gamma;

    // Common factors
    /** 1 / A */
    private double ooA;
    /** B / A */
    private double BoA;
    /** 1 / (A * B) */
    private double ooAB;
    /** C / (2 * A * B) */
    private double Co2AB;
    /** 1 / (1 + B) */
    private double ooBpo;

    /**
     * DSST model needs equinoctial orbit as internal representation. Classical equinoctial elements
     * have discontinuities when inclination is close to zero. In this representation, I = +1. <br>
     * To avoid this discontinuity, another representation exists and equinoctial elements can be
     * expressed in a different way, called "retrograde" orbit. This implies I = -1. As Orekit
     * doesn't implement the retrograde orbit, I = 1 here.
     */
    private int  I = 1;

    /** Default constructor.
     * @param body the third body to consider
     * (ex: {@link org.orekit.bodies.CelestialBodyFactory#getSun()} or
     * {@link org.orekit.bodies.CelestialBodyFactory#getMoon()})
     */
    public DSSTThirdBody(final CelestialBody body) {
        this(body, DEFAULT_ORDER, DEFAULT_EPSILON);
    }

    /** Default constructor.
     * @param body the third body to consider
     * (ex: {@link org.orekit.bodies.CelestialBodyFactory#getSun()} or
     * {@link org.orekit.bodies.CelestialBodyFactory#getMoon()})
     * @param order N order for summation
     */
    public DSSTThirdBody(final CelestialBody body,
                         final int order) {
        this(body, order, DEFAULT_EPSILON);
    }

    /** Complete constructor.
     * @param body the third body to consider
     * (ex: {@link org.orekit.bodies.CelestialBodyFactory#getSun()} or
     * {@link org.orekit.bodies.CelestialBodyFactory#getMoon()})
     * @param order N order for summation
     * @param epsilon convergence parameter used in Hansen coefficient generation
     */
    public DSSTThirdBody(final CelestialBody body,
                         final int order,
                         final double epsilon) {
        this.body = body;
        this.gm   = body.getGM();

        this.epsilon = epsilon;
        this.order = order;
        this.Vns = CoefficientFactory.computeVnsCoefficient(order + 1);
    }

    /** {@inheritDoc} */
    public double[] getMeanElementRate(final SpacecraftState currentState) throws OrekitException {

        // Compute useful equinoctial parameters
        computeParameters(currentState);

        // Compute potential U derivatives
        final double[] potentialDerivatives = computeUDerivatives(currentState);
        final double dUda  = potentialDerivatives[0];
        final double dUdk  = potentialDerivatives[1];
        final double dUdh  = potentialDerivatives[2];
        final double dUdAl = potentialDerivatives[3];
        final double dUdBe = potentialDerivatives[4];
        final double dUdGa = potentialDerivatives[5];

        // Compute cross derivatives from formula 2.2 - (8):
        // U(alpha,gamma) = alpha * dU/dgamma - gamma * dU/dalpha
        final double UAlphaGamma = alpha * dUdGa - gamma * dUdAl;
        // U(beta,gamma)  =  beta * dU/dgamma - gamma * dU/dbeta
        final double UBetaGamma  = beta * dUdGa - gamma * dUdBe;

        final double pUmIqUoAB = (p * UAlphaGamma - I * q * UBetaGamma) * ooAB;

        // Compute mean element rates from equation 3.1-(1)
        final double da =  0.;
        final double dh =  BoA * dUdk + k * pUmIqUoAB;
        final double dk = -BoA * dUdh - h * pUmIqUoAB;
        final double dp = -Co2AB * UBetaGamma;
        final double dq = -I * Co2AB * UAlphaGamma;
        final double dM = -2 * a * ooA * dUda + BoA * ooBpo * (h * dUdh + k * dUdk) + pUmIqUoAB;

        return new double[] { da, dk, dh, dq, dp, dM };

    }

    /** {@inheritDoc} */
    public double[] getShortPeriodicVariations(final AbsoluteDate date, final double[] meanElements)
        throws OrekitException {
        // TODO: not implemented yet
        // Short Periodic Variations are set to null
        return new double[] {0.,0.,0.,0.,0.,0.};
    }

    /** {@inheritDoc} */
    public void init(SpacecraftState state) {

    }

    /** Compute useful parameters: A, B, C, &alpha;, &beta;, &gamma;.
     *  @param state current state information: date, kinematics, attitude
     *  @throws OrekitException 
     */
    private void computeParameters(final SpacecraftState state) throws OrekitException {
        // Get current state vector as equinoctial elements:
        double[] stateVector = new double[6];
        ORBIT_TYPE.mapOrbitToArray(state.getOrbit(), ANGLE_TYPE, stateVector);
    
        // Equinoctial elements
        a = stateVector[0];
        k = stateVector[1];
        h = stateVector[2];
        q = stateVector[3];
        p = stateVector[4];
    
        // Factors
        final double q2 = q * q;
        final double p2 = p * p;

        // Equinoctial coefficients
        A  = FastMath.sqrt(state.getMu() * a);
        B  = FastMath.sqrt(1 - k * k - h * h);
        C  = 1 + q2 + p2;

        // Distance from center of mass of the central body to the 3rd body
        final Vector3D bodyPos = body.getPVCoordinates(state.getDate(), state.getFrame()).getPosition();
        R3 = bodyPos.getNorm();

        // Direction cosines
        final Vector3D f = new Vector3D( (1 - p2 + q2) / C,        2. * p * q / C,       -2. * I * p / C);
        final Vector3D g = new Vector3D(2. * I * p * q / C, I * (1 + p2 - q2) / C,            2. * q / C);
        final Vector3D w = new Vector3D(        2. * p / C,           -2. * q / C, I * (1 - p2 - q2) / C);
        final Vector3D bodyDir = bodyPos.normalize();
        alpha = bodyDir.dotProduct(f);
        beta  = bodyDir.dotProduct(g);
        gamma = bodyDir.dotProduct(w);

        // Common factors
        ooA   = 1. / A ;
        BoA   = B * ooA;
        ooAB  = ooA / B;
        Co2AB = C * ooAB / 2.;
        ooBpo = 1. / (1. + B);
    }

    private double[] computeUDerivatives(final SpacecraftState state) throws OrekitException {

        // Hansen coefficients
        final HansenCoefficients hansen = new HansenCoefficients(state.getE(), epsilon);
        // Gs coefficients
        final double[][] GsHs = CoefficientFactory.computeGsHsCoefficient(k, h, alpha, beta, order+1);
        // Qns coefficients
        final double[][] Qns = CoefficientFactory.computeQnsCoefficient(order+1, gamma);
        // mu3 / R3
        final double muoR3 = gm / R3;
        // a / R3
        final double aoR3  =  a / R3;
        // chi = 1 / B
        final double chi3 = FastMath.pow(B, -3);

        // Potential derivatives
        double dUda  = 0.;
        double dUdk  = 0.;
        double dUdh  = 0.;
        double dUdAl = 0.;
        double dUdBe = 0.;
        double dUdGa = 0.;
    
        for (int s = 0; s < order - 1; s++) {
            // Get the current Gs and Hs coefficient
            final double gs   = GsHs[0][s];
            final double gsM1 = (s > 0 ? GsHs[0][s - 1] : 0);
            final double hsM1 = (s > 0 ? GsHs[1][s - 1] : 0);

            // Compute partial derivatives of Gs
            final double dGsdk  = s *  beta * gsM1 - s * alpha * hsM1;
            final double dGsdh  = s * alpha * gsM1 + s *  beta * hsM1;
            final double dGsdAl = s *     k * gsM1 - s *     h * hsM1;
            final double dGsdBe = s *     h * gsM1 + s *     k * hsM1;
    
            // Kronecker symbol (2 - delta(0,s))
            final double delta0s = (s == 0) ? 1. : 2.;
    
            for (int n = s + 2; n <= order; n++) {
                // Extract data from previous computation :
                final double vns = Vns.get(new NSKey(n, s));
    
                final double kns   = hansen.getHansenKernelValue(0, n, s);
                final double qns   = Qns[n][s];
                final double aoR3n = FastMath.pow(aoR3, n);
                final double dkns  = hansen.getHansenKernelDerivative(0, n, s);
                final double coef0 = delta0s * aoR3n * vns;
                final double coef1 = coef0 * qns;
    
                // Compute dU / da :
                dUda += coef1 * n * kns * gs;
                // Compute dU / dEx
                dUdk += coef1 * (kns * dGsdk + k * chi3 * gs * dkns);
                // Compute dU / dEy
                dUdh += coef1 * (kns * dGsdh + h * chi3 * gs * dkns);
                // Compute dU / dAlpha
                dUdAl += coef1 * kns * dGsdAl;
                // Compute dU / dBeta
                dUdBe += coef1 * kns * dGsdBe;
                // Compute dU / dGamma with dQns/dGamma = Q(n, s + 1) from Equation 3.1-(8)
                dUdGa += coef0 * kns * Qns[n][s + 1] * gs;
            }
        }
    
        dUda  *= (muoR3 / a);
        dUdk  *= muoR3;
        dUdh  *= muoR3;
        dUdAl *= muoR3;
        dUdBe *= muoR3;
        dUdGa *= muoR3;
    
        return new double[] { dUda, dUdk, dUdh, dUdAl, dUdBe, dUdGa };
    
    }

}
