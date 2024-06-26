/*
 * Copyright (C) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (C) 1998 Ross Ihaka
 * Copyright (c) 1998--2014, The R Core Team
 * Copyright (c) 2002--2010, The R Foundation
 * Copyright (C) 2005--2006, Morten Welinder
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates
 *
 * based on AS 91 (C) 1979 Royal Statistical Society
 *  and  on AS 111 (C) 1977 Royal Statistical Society
 *  and  on AS 241 (C) 1988 Royal Statistical Society
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.runtime.nmath;

import static com.oracle.truffle.r.runtime.nmath.DPQ.rd0;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rd1;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdexp;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdt0;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdt1;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdtclog;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rdtqiv;
import static com.oracle.truffle.r.runtime.nmath.DPQ.rlog1exp;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_EPSILON;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_MANT_DIG;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_MAX_EXP;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_MIN;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_MIN_EXP;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.M_LOG10_2;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.M_LN2;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.M_PI;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.ML_NAN;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.ML_POSINF;
import static com.oracle.truffle.r.runtime.nmath.RMath.fmax2;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.common.SuppressFBWarnings;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.nmath.DPQ.EarlyReturn;
import com.oracle.truffle.r.runtime.nmath.RMathError.MLError;
import com.oracle.truffle.r.runtime.nmath.distr.DNorm;
import com.oracle.truffle.r.runtime.nmath.distr.DPois;
import com.oracle.truffle.r.runtime.nmath.distr.Pnorm;
import com.oracle.truffle.r.runtime.nmath.distr.Qnorm;

/**
 * Java implementation of the qgamma and related functions. The logic was derived from GNU R (see
 * inline comments).
 */
public abstract class GammaFunctions {

    // This is derived from distn.c.

    // The remainder of this file is derived from GNU R (mostly nmath): qgamma.c, nmath.h, lgamma.c,
    // gamma.c, stirlerr.c, lgammacor.c, pgamma.c, fmax2.c, dpois.c, bd0.c, dgamma.c, pnorm.c,
    // qnorm.c

    // TODO Many of the functions below that are not directly supporting qgamma should eventually be
    // factored out to support those R functions they represent. Note 1/2017: some have been
    // factored out.

    //
    // lgammacor
    //

    @CompilationFinal(dimensions = 1) private static final double[] ALGMCS = new double[]{+.1666389480451863247205729650822e+0, -.1384948176067563840732986059135e-4,
                    +.9810825646924729426157171547487e-8,
                    -.1809129475572494194263306266719e-10, +.6221098041892605227126015543416e-13, -.3399615005417721944303330599666e-15, +.2683181998482698748957538846666e-17,
                    -.2868042435334643284144622399999e-19, +.3962837061046434803679306666666e-21, -.6831888753985766870111999999999e-23, +.1429227355942498147573333333333e-24,
                    -.3547598158101070547199999999999e-26, +.1025680058010470912000000000000e-27, -.3401102254316748799999999999999e-29, +.1276642195630062933333333333333e-30};

    /*
     * For IEEE double precision DBL_EPSILON = 2^-52 = 2.220446049250313e-16 : xbig = 2 ^ 26.5 xmax
     * = DBL_MAX / 48 = 2^1020 / 3
     */
    private static final int nalgm = 5;
    private static final double xbig = 94906265.62425156;
    private static final double lgc_xmax = 3.745194030963158e306;

    static double lgammacor(double x) {
        double tmp;

        if (x < 10) {
            return RMathError.defaultError();
        } else if (x >= lgc_xmax) {
            /* allow to underflow below */
            RMathError.error(MLError.UNDERFLOW, "lgammacor");
        } else if (x < xbig) {
            tmp = 10 / x;
            return RMath.chebyshevEval(tmp * tmp * 2 - 1, ALGMCS, nalgm) / x;
        }
        return 1 / (x * 12);
    }

    /**
     * Should be used in places where GnuR itself uses std libc function {@code lgamma}. Under the
     * hood it uses {@link #lgammafn(double)}.
     */
    public static double lgamma(double x) {
        return lgammafn(x);
    }

    //
    // gammafn
    //

    @CompilationFinal(dimensions = 1) private static final double[] GAMCS = new double[]{+.8571195590989331421920062399942e-2, +.4415381324841006757191315771652e-2,
                    +.5685043681599363378632664588789e-1,
                    -.4219835396418560501012500186624e-2, +.1326808181212460220584006796352e-2, -.1893024529798880432523947023886e-3, +.3606925327441245256578082217225e-4,
                    -.6056761904460864218485548290365e-5, +.1055829546302283344731823509093e-5, -.1811967365542384048291855891166e-6, +.3117724964715322277790254593169e-7,
                    -.5354219639019687140874081024347e-8, +.9193275519859588946887786825940e-9, -.1577941280288339761767423273953e-9, +.2707980622934954543266540433089e-10,
                    -.4646818653825730144081661058933e-11, +.7973350192007419656460767175359e-12, -.1368078209830916025799499172309e-12, +.2347319486563800657233471771688e-13,
                    -.4027432614949066932766570534699e-14, +.6910051747372100912138336975257e-15, -.1185584500221992907052387126192e-15, +.2034148542496373955201026051932e-16,
                    -.3490054341717405849274012949108e-17, +.5987993856485305567135051066026e-18, -.1027378057872228074490069778431e-18, +.1762702816060529824942759660748e-19,
                    -.3024320653735306260958772112042e-20, +.5188914660218397839717833550506e-21, -.8902770842456576692449251601066e-22, +.1527474068493342602274596891306e-22,
                    -.2620731256187362900257328332799e-23, +.4496464047830538670331046570666e-24, -.7714712731336877911703901525333e-25, +.1323635453126044036486572714666e-25,
                    -.2270999412942928816702313813333e-26, +.3896418998003991449320816639999e-27, -.6685198115125953327792127999999e-28, +.1146998663140024384347613866666e-28,
                    -.1967938586345134677295103999999e-29, +.3376448816585338090334890666666e-30, -.5793070335782135784625493333333e-31};

    /*
     * For IEEE double precision DBL_EPSILON = 2^-52 = 2.220446049250313e-16 : (xmin, xmax) are
     * non-trivial, see ./gammalims.c xsml = exp(.01)*DBL_MIN dxrel = sqrt(DBL_EPSILON) = 2 ^ -26
     */
    private static final int ngam = 22;
    static final double gfn_xmin = -170.5674972726612;
    static final double gfn_xmax = 171.61447887182298;
    private static final double gfn_xsml = 2.2474362225598545e-308;
    private static final double gfn_dxrel = 1.490116119384765696e-8;

    private static final double M_LN_SQRT_2PI = 0.918938533204672741780329736406;

    public static double gammafn(double x) {
        int i;
        int n;
        double y;
        double sinpiy;
        double value;

        if (Double.isNaN(x)) {
            return x;
        }

        /*
         * If the argument is exactly zero or a negative integer then return NaN.
         */
        if (x == 0 || (x < 0 && x == (long) x)) {
            // ML_ERROR(ME_DOMAIN, "gammafn");
            return Double.NaN;
        }

        y = Math.abs(x);

        if (y <= 10) {

            /*
             * Compute gamma(x) for -10 <= x <= 10 Reduce the interval and find gamma(1 + y) for 0
             * <= y < 1 first of all.
             */

            n = (int) x;
            if (x < 0) {
                --n;
            }
            y = x - n; /* n = floor(x) ==> y in [ 0, 1 ) */
            --n;
            value = RMath.chebyshevEval(y * 2 - 1, GAMCS, ngam) + .9375;
            if (n == 0) {
                return value; /* x = 1.dddd = 1+y */
            }

            if (n < 0) {
                /* compute gamma(x) for -10 <= x < 1 */

                /* exact 0 or "-n" checked already above */

                /* The answer is less than half precision */
                /* because x too near a negative integer. */
                if (x < -0.5 && Math.abs(x - (int) (x - 0.5) / x) < gfn_dxrel) {
                    RMathError.error(MLError.PRECISION, "gammafn");
                }

                /* The argument is so close to 0 that the result would overflow. */
                if (y < gfn_xsml) {
                    // ML_ERROR(ME_RANGE, "gammafn");
                    if (x > 0) {
                        return Double.POSITIVE_INFINITY;
                    } else {
                        return Double.NEGATIVE_INFINITY;
                    }
                }

                n = -n;

                for (i = 0; i < n; i++) {
                    value /= (x + i);
                }
                return value;
            } else {
                /* gamma(x) for 2 <= x <= 10 */

                for (i = 1; i <= n; i++) {
                    value *= (y + i);
                }
                return value;
            }
        } else {
            /* gamma(x) for y = |x| > 10. */

            if (x > gfn_xmax) { /* Overflow */
                // ML_ERROR(ME_RANGE, "gammafn");
                return Double.POSITIVE_INFINITY;
            }

            if (x < gfn_xmin) { /* Underflow */
                // ML_ERROR(ME_UNDERFLOW, "gammafn");
                return 0.;
            }

            if (y <= 50 && y == (int) y) { /* compute (n - 1)! */
                value = 1.;
                for (i = 2; i < y; i++) {
                    value *= i;
                }
            } else { /* normal case */
                value = Math.exp((y - 0.5) * Math.log(y) - y + M_LN_SQRT_2PI + (Utils.identityEquals(2 * y, (int) (2 * y)) ? RMath.stirlerr(y) : lgammacor(y)));
            }
            if (x > 0) {
                return value;
            }

            if (Math.abs((x - (int) (x - 0.5)) / x) < gfn_dxrel) {
                /* The answer is less than half precision because */
                /* the argument is too near a negative integer. */
                RMathError.error(MLError.PRECISION, "gammafn");
            }

            sinpiy = Math.sin(Math.PI * y);
            if (sinpiy == 0) { /* Negative integer arg - overflow */
                RMathError.error(MLError.RANGE, "gammafn");
                return Double.POSITIVE_INFINITY;
            }

            return -Math.PI / (y * sinpiy * value);
        }
    }

    //
    // lgammafn
    //

    /*
     * For IEEE double precision DBL_EPSILON = 2^-52 = 2.220446049250313e-16 : xmax = DBL_MAX /
     * log(DBL_MAX) = 2^1024 / (1024 * log(2)) = 2^1014 / log(2) dxrel = sqrt(DBL_EPSILON) = 2^-26 =
     * 5^26 * 1e-26 (is *exact* below !)
     */
    private static final double gfn_sign_xmax = 2.5327372760800758e+305;
    private static final double gfn_sign_dxrel = 1.490116119384765696e-8;

    private static final double M_LN_SQRT_PId2 = 0.225791352644727432363097614947;

    // convert C's int* sgn to a 1-element int[]
    public static double lgammafnSign(double x, int[] sgn) {
        double ans;
        double y;
        double sinpiy;

        if (sgn != null) {
            sgn[0] = 1;
        }

        if (Double.isNaN(x)) {
            return x;
        }

        if (sgn != null && x < 0 && RMath.fmod(Math.floor(-x), 2.) == 0) {
            sgn[0] = -1;
        }

        if (x <= 0 && x == (long) x) { /* Negative integer argument */
            return Double.POSITIVE_INFINITY; /* +Inf, since lgamma(x) = log|gamma(x)| */
        }

        y = Math.abs(x);

        if (y < 1e-306) {
            return -Math.log(x); // denormalized range, R change
        }
        if (y <= 10) {
            return Math.log(Math.abs(gammafn(x)));
        }
        /*
         * ELSE y = |x| > 10 ----------------------
         */

        if (y > gfn_sign_xmax) {
            return Double.POSITIVE_INFINITY;
        }

        if (x > 0) { /* i.e. y = x > 10 */
            if (x > 1e17) {
                return (x * (Math.log(x) - 1.));
            } else if (x > 4934720.) {
                return (M_LN_SQRT_2PI + (x - 0.5) * Math.log(x) - x);
            } else {
                return M_LN_SQRT_2PI + (x - 0.5) * Math.log(x) - x + lgammacor(x);
            }
        }
        /* else: x < -10; y = -x */
        sinpiy = Math.abs(Math.sin(Math.PI * y));

        if (sinpiy == 0) { /*
                            * Negative integer argument === Now UNNECESSARY: caught above
                            */
            RMathError.warning(Message.GENERIC, " ** should NEVER happen! *** [lgamma.c: Neg.int]");
            return RMathError.defaultError();
        }

        ans = M_LN_SQRT_PId2 + (x - 0.5) * Math.log(y) - x - Math.log(sinpiy) - lgammacor(y);

        if (Math.abs((x - (long) (x - 0.5)) * ans / x) < gfn_sign_dxrel) {
            /*
             * The answer is less than half precision because the argument is too near a negative
             * integer.
             */
            RMathError.error(MLError.PRECISION, "lgamma");
        }

        return ans;
    }

    @TruffleBoundary
    public static double lgammafn(double x) {
        return lgammafnSign(x, new int[1]);
    }

    //
    // qgamma
    //

    private static final double C7 = 4.67;
    private static final double C8 = 6.66;
    private static final double C9 = 6.73;
    private static final double C10 = 13.32;

    private static double qchisqAppr(double p, double nu, double g /* = log Gamma(nu/2) */, boolean lowerTail, boolean logp, double tol /*
                                                                                                                                         * EPS1
                                                                                                                                         */) {
        double alpha;
        double a;
        double c;
        double ch;
        double p1;
        double p2;
        double q;
        double t;
        double x;

        /* test arguments and initialise */
        if (Double.isNaN(p) || Double.isNaN(nu)) {
            return p + nu;
        }

        try {
            DPQ.rqp01check(p, logp);
        } catch (EarlyReturn e) {
            return e.result;
        }

        if (nu <= 0) {
            return RMathError.defaultError();
        }

        alpha = 0.5 * nu; /* = [pq]gamma() shape */
        c = alpha - 1;

        if (nu < (-1.24) * (p1 = DPQ.rdtlog(p, lowerTail, logp))) { /* for small chi-squared */
            /*
             * log(alpha) + g = log(alpha) + log(gamma(alpha)) = = log(alpha*gamma(alpha)) =
             * lgamma(alpha+1) suffers from catastrophic cancellation when alpha << 1
             */
            double lgam1pa = (alpha < 0.5) ? lgamma1p(alpha) : (Math.log(alpha) + g);
            ch = Math.exp((lgam1pa + p1) / alpha + M_LN2);
        } else if (nu > 0.32) { /* using Wilson and Hilferty estimate */
            x = Qnorm.qnorm(p, 0, 1, lowerTail, logp);
            p1 = 2. / (9 * nu);
            ch = nu * Math.pow(x * Math.sqrt(p1) + 1 - p1, 3);

            /* approximation for p tending to 1: */
            if (ch > 2.2 * nu + 6) {
                ch = -2 * (rdtclog(p, lowerTail, logp) - c * Math.log(0.5 * ch) + g);
            }
        } else { /* "small nu" : 1.24*(-log(p)) <= nu <= 0.32 */
            ch = 0.4;
            a = rdtclog(p, lowerTail, logp) + g + c * M_LN2;
            do {
                q = ch;
                p1 = 1. / (1 + ch * (C7 + ch));
                p2 = ch * (C9 + ch * (C8 + ch));
                t = -0.5 + (C7 + 2 * ch) * p1 - (C9 + ch * (C10 + 3 * ch)) / p2;
                ch -= (1 - Math.exp(a + 0.5 * ch) * p2 * p1) / t;
            } while (Math.abs(q - ch) > tol * Math.abs(ch));
        }

        return ch;
    }

    private static final double EPS1 = 1e-2;
    private static final double EPS2 = 5e-7; /* final precision of AS 91 */
    private static final double EPS_N = 1e-15; /* precision of Newton step / iterations */

    private static final int MAXIT = 1000; /* was 20 */

    private static final double pMIN = 1e-100; /* was 0.000002 = 2e-6 */
    private static final double pMAX = (1 - 1e-14); /* was (1-1e-12) and 0.999998 = 1 - 2e-6 */

    private static final double i420 = 1.0 / 420;
    private static final double i2520 = 1.0 / 2520;
    private static final double i5040 = 1.0 / 5040;

    @TruffleBoundary
    public static double qgamma(double p, double alpha, double scale, boolean lowerTail, boolean logp) {
        double pu;
        double a;
        double b;
        double c;
        double g;
        double ch;
        double ch0;
        double p1;
        double p2;
        double q;
        double s1;
        double s2;
        double s3;
        double s4;
        double s5;
        double s6;
        double t;
        double x;
        int i;
        int maxItNewton = 1;

        double localP = p;
        boolean localLogp = logp;

        /* test arguments and initialise */
        if (Double.isNaN(localP) || Double.isNaN(alpha) || Double.isNaN(scale)) {
            return localP + alpha + scale;
        }

        // expansion of R_Q_P01_boundaries(p, 0., ML_POSINF)
        if (localLogp) {
            if (localP > 0) {
                return RMathError.defaultError();
            }
            if (localP == 0) { /* upper bound */
                return lowerTail ? Double.POSITIVE_INFINITY : 0;
            }
            if (localP == Double.NEGATIVE_INFINITY) {
                return lowerTail ? 0 : Double.POSITIVE_INFINITY;
            }
        } else { /* !log_p */
            if (localP < 0 || localP > 1) {
                return RMathError.defaultError();
            }
            if (localP == 0) {
                return lowerTail ? 0 : Double.POSITIVE_INFINITY;
            }
            if (localP == 1) {
                return lowerTail ? Double.POSITIVE_INFINITY : 0;
            }
        }

        if (alpha < 0 || scale <= 0) {
            return RMathError.defaultError();
        }

        if (alpha == 0) {
            /* all mass at 0 : */
            return 0;
        }

        if (alpha < 1e-10) {
            maxItNewton = 7; /* may still be increased below */
        }

        pu = rdtqiv(localP, lowerTail, localLogp); /* lower_tail prob (in any case) */

        g = lgammafn(alpha); /* log Gamma(v/2) */

        /*----- Phase I : Starting Approximation */
        PHASE1: do { // emulate C goto with do-while loop and breaks
            ch = qchisqAppr(localP, /* nu= 'df' = */2 * alpha, /* lgamma(nu/2)= */g, lowerTail, localLogp, /*
                                                                                                            * tol
                                                                                                            * =
                                                                                                            */EPS1);
            if (!RRuntime.isFinite(ch)) {
                /* forget about all iterations! */
                maxItNewton = 0;
                break PHASE1;
            }
            if (ch < EPS2) { /* Corrected according to AS 91; MM, May 25, 1999 */
                maxItNewton = 20;
                break PHASE1; /* and do Newton steps */
            }

            /*
             * FIXME: This (cutoff to {0, +Inf}) is far from optimal ----- when log_p or
             * !lower_tail, but NOT doing it can be even worse
             */
            if (pu > pMAX || pu < pMIN) {
                /* did return ML_POSINF or 0.; much better: */
                maxItNewton = 20;
                break PHASE1; /* and do Newton steps */
            }

            /*----- Phase II: Iteration
             *  Call pgamma() [AS 239]  and calculate seven term taylor series
             */
            c = alpha - 1;
            s6 = (120 + c * (346 + 127 * c)) * i5040; /* used below, is "const" */

            ch0 = ch; /* save initial approx. */
            for (i = 1; i <= MAXIT; i++) {
                q = ch;
                p1 = 0.5 * ch;
                p2 = pu - pgammaRaw(p1, alpha, /* lower_tail */true, /* log_p */false);
                if (!RRuntime.isFinite(p2) || ch <= 0) {
                    ch = ch0;
                    maxItNewton = 27;
                    break PHASE1;
                } /* was return ML_NAN; */

                t = p2 * Math.exp(alpha * M_LN2 + g + p1 - c * Math.log(ch));
                b = t / ch;
                a = 0.5 * t - b * c;
                s1 = (210 + a * (140 + a * (105 + a * (84 + a * (70 + 60 * a))))) * i420;
                s2 = (420 + a * (735 + a * (966 + a * (1141 + 1278 * a)))) * i2520;
                s3 = (210 + a * (462 + a * (707 + 932 * a))) * i2520;
                s4 = (252 + a * (672 + 1182 * a) + c * (294 + a * (889 + 1740 * a))) * i5040;
                s5 = (84 + 2264 * a + c * (1175 + 606 * a)) * i2520;
                ch += t * (1 + 0.5 * t * s1 - b * c * (s1 - b * (s2 - b * (s3 - b * (s4 - b * (s5 - b * s6))))));
                if (Math.abs(q - ch) < EPS2 * ch) {
                    break PHASE1;
                }
                if (Math.abs(q - ch) > 0.1 * ch) { /* diverging? -- also forces ch > 0 */
                    if (ch < q) {
                        ch = 0.9 * q;
                    } else {
                        ch = 1.1 * q;
                    }
                }
            }
            /* no convergence in MAXIT iterations -- but we add Newton now... */
            /*
             * was ML_ERROR(ME_PRECISION, "qgamma"); does nothing in R !
             */
        } while (false); // implicit break at end

        /* END: */// this is where the breaks in PHASE1 jump (originally by goto)
        /*
         * PR# 2214 : From: Morten Welinder <terra@diku.dk>, Fri, 25 Oct 2002 16:50 -------- To:
         * R-bugs@biostat.ku.dk Subject: qgamma precision
         *
         * With a final Newton step, double accuracy, e.g. for (p= 7e-4; nu= 0.9)
         *
         * Improved (MM): - only if rel.Err > EPS_N (= 1e-15); - also for lower_tail = FALSE or
         * log_p = TRUE - optionally *iterate* Newton
         */
        x = 0.5 * scale * ch;
        if (maxItNewton > 0) {
            /* always use log scale */
            if (!localLogp) {
                localP = Math.log(localP);
                localLogp = true;
            }
            if (x == 0) {
                final double u1p = 1. + 1e-7;
                final double u1m = 1. - 1e-7;
                x = DBL_MIN;
                pu = pgamma(x, alpha, scale, lowerTail, localLogp);
                if ((lowerTail && pu > localP * u1p) || (!lowerTail && pu < localP * u1m)) {
                    return 0.;
                }
                /* else: continue, using x = DBL_MIN instead of 0 */
            } else {
                pu = pgamma(x, alpha, scale, lowerTail, localLogp);
            }

            if (pu == Double.NEGATIVE_INFINITY) {
                return 0; /* PR#14710 */
            }
            for (i = 1; i <= maxItNewton; i++) {
                p1 = pu - localP;
                if (Math.abs(p1) < Math.abs(EPS_N * localP)) {
                    break;
                }
                /* else */
                if ((g = dgamma(x, alpha, scale, localLogp)) == rd0(localLogp)) {
                    break;
                }
                /*
                 * else : delta x = f(x)/f'(x); if(log_p) f(x) := log P(x) - p; f'(x) = d/dx log
                 * P(x) = P' / P ==> f(x)/f'(x) = f*P / P' = f*exp(p_) / P' (since p_ = log P(x))
                 */
                t = localLogp ? p1 * Math.exp(pu - g) : p1 / g; /* = "delta x" */
                t = lowerTail ? x - t : x + t;
                pu = pgamma(t, alpha, scale, lowerTail, localLogp);
                if (Math.abs(pu - localP) > Math.abs(p1) || (i > 1 && Math.abs(pu - localP) == Math.abs(p1))) {
                    // second condition above: against flip-flop
                    /* no improvement */
                    break;
                } /* else : */
                x = t;
            }
        }

        return x;
    }

    //
    // pgamma
    //

    /*
     * Continued fraction for calculation of 1/i + x/(i+d) + x^2/(i+2*d) + x^3/(i+3*d) + ... =
     * sum_{k=0}^Inf x^k/(i+k*d)
     *
     * auxilary in log1pmx() and lgamma1p()
     */
    private static double logcf(double x, double i, double d, double eps /* ~ relative tolerance */) {
        double c1 = 2 * d;
        double c2 = i + d;
        double c4 = c2 + d;
        double a1 = c2;
        double b1 = i * (c2 - i * x);
        double b2 = d * d * x;
        double a2 = c4 * c2 - b2;

        b2 = c4 * b1 - i * b2;

        while (Math.abs(a2 * b1 - a1 * b2) > Math.abs(eps * b1 * b2)) {
            double c3 = c2 * c2 * x;
            c2 += d;
            c4 += d;
            a1 = c4 * a2 - c3 * a1;
            b1 = c4 * b2 - c3 * b1;

            c3 = c1 * c1 * x;
            c1 += d;
            c4 += d;
            a2 = c4 * a1 - c3 * a2;
            b2 = c4 * b1 - c3 * b2;

            if (Math.abs(b2) > scalefactor) {
                a1 /= scalefactor;
                b1 /= scalefactor;
                a2 /= scalefactor;
                b2 /= scalefactor;
            } else if (Math.abs(b2) < 1 / scalefactor) {
                a1 *= scalefactor;
                b1 *= scalefactor;
                a2 *= scalefactor;
                b2 *= scalefactor;
            }
        }

        return a2 / b2;
    }

    private static final double minLog1Value = -0.79149064;

    /* Accurate calculation of log(1+x)-x, particularly for small x. */
    public static double log1pmx(double x) {
        if (x > 1 || x < minLog1Value) {
            return RMath.log1p(x) - x;
        } else { /*
                  * -.791 <= x <= 1 -- expand in [x/(2+x)]^2 =: y : log(1+x) - x = x/(2+x) * [ 2 * y
                  * * S(y) - x], with --------------------------------------------- S(y) = 1/3 + y/5
                  * + y^2/7 + ... = \sum_{k=0}^\infty y^k / (2k + 3)
                  */
            double r = x / (2 + x);
            double y = r * r;
            if (Math.abs(x) < 1e-2) {
                final double two = 2;
                return r * ((((two / 9 * y + two / 7) * y + two / 5) * y + two / 3) * y - x);
            } else {
                return r * (2 * y * logcf(y, 3, 2, tol_logcf) - x);
            }
        }
    }

    private static final double eulers_const = 0.5772156649015328606065120900824024;

    /* coeffs[i] holds (zeta(i+2)-1)/(i+2) , i = 0:(N-1), N = 40 : */
    private static final int N = 40;
    @CompilationFinal(dimensions = 1) private static final double[] coeffs = new double[]{0.3224670334241132182362075833230126e-0, 0.6735230105319809513324605383715000e-1,
                    0.2058080842778454787900092413529198e-1,
                    0.7385551028673985266273097291406834e-2, 0.2890510330741523285752988298486755e-2, 0.1192753911703260977113935692828109e-2, 0.5096695247430424223356548135815582e-3,
                    0.2231547584535793797614188036013401e-3, 0.9945751278180853371459589003190170e-4, 0.4492623673813314170020750240635786e-4, 0.2050721277567069155316650397830591e-4,
                    0.9439488275268395903987425104415055e-5, 0.4374866789907487804181793223952411e-5, 0.2039215753801366236781900709670839e-5, 0.9551412130407419832857179772951265e-6,
                    0.4492469198764566043294290331193655e-6, 0.2120718480555466586923135901077628e-6, 0.1004322482396809960872083050053344e-6, 0.4769810169363980565760193417246730e-7,
                    0.2271109460894316491031998116062124e-7, 0.1083865921489695409107491757968159e-7, 0.5183475041970046655121248647057669e-8, 0.2483674543802478317185008663991718e-8,
                    0.1192140140586091207442548202774640e-8, 0.5731367241678862013330194857961011e-9, 0.2759522885124233145178149692816341e-9, 0.1330476437424448948149715720858008e-9,
                    0.6422964563838100022082448087644648e-10, 0.3104424774732227276239215783404066e-10, 0.1502138408075414217093301048780668e-10, 0.7275974480239079662504549924814047e-11,
                    0.3527742476575915083615072228655483e-11, 0.1711991790559617908601084114443031e-11, 0.8315385841420284819798357793954418e-12, 0.4042200525289440065536008957032895e-12,
                    0.1966475631096616490411045679010286e-12, 0.9573630387838555763782200936508615e-13, 0.4664076026428374224576492565974577e-13, 0.2273736960065972320633279596737272e-13,
                    0.1109139947083452201658320007192334e-13};

    private static final double C = 0.2273736845824652515226821577978691e-12; /* zeta(N+2)-1 */
    private static final double tol_logcf = 1e-14;

    /* Compute log(gamma(a+1)) accurately also for small a (0 < a < 0.5). */
    public static double lgamma1p(double a) {
        double lgam;
        int i;

        if (Math.abs(a) >= 0.5) {
            return lgammafn(a + 1);
        }

        /*
         * Abramowitz & Stegun 6.1.33 : for |x| < 2, <==> log(gamma(1+x)) = -(log(1+x) - x) -
         * gamma*x + x^2 * \sum_{n=0}^\infty c_n (-x)^n where c_n := (Zeta(n+2) - 1)/(n+2) =
         * coeffs[n]
         *
         * Here, another convergence acceleration trick is used to compute lgam(x) := sum_{n=0..Inf}
         * c_n (-x)^n
         */
        lgam = C * logcf(-a / 2, N + 2, 1, tol_logcf);
        for (i = N - 1; i >= 0; i--) {
            lgam = coeffs[i] - a * lgam;
        }

        return (a * lgam - eulers_const) * a - log1pmx(a);
    } /* lgamma1p */

    /* If |x| > |k| * M_cutoff, then log[ exp(-x) * k^x ] =~= -x */
    private static final double M_cutoff = M_LN2 * Double.MAX_EXPONENT / DBL_EPSILON;

    /*
     * dpois_wrap (x_P_1, lambda, g_log) == dpois (x_P_1 - 1, lambda, g_log) := exp(-L) L^k /
     * gamma(k+1) , k := x_P_1 - 1
     */
    private static double dpoisWrap(double xplus1, double lambda, boolean giveLog) {
        if (!RRuntime.isFinite(lambda)) {
            return rd0(giveLog);
        }
        if (xplus1 > 1) {
            return DPois.dpoisRaw(xplus1 - 1, lambda, giveLog);
        }
        if (lambda > Math.abs(xplus1 - 1) * M_cutoff) {
            return rdexp(-lambda - lgammafn(xplus1), giveLog);
        } else {
            double d = DPois.dpoisRaw(xplus1, lambda, giveLog);
            return giveLog ? d + Math.log(xplus1 / lambda) : d * (xplus1 / lambda);
        }
    }

    /*
     * Abramowitz and Stegun 6.5.29 [right]
     */
    private static double pgammaSmallx(double x, double alph, boolean lowerTail, boolean logp) {
        double sum = 0;
        double c = alph;
        double n = 0;
        double term;

        /*
         * Relative to 6.5.29 all terms have been multiplied by alph and the first, thus being 1, is
         * omitted.
         */

        do {
            n++;
            c *= -x / n;
            term = c / (alph + n);
            sum += term;
        } while (Math.abs(term) > DBL_EPSILON * Math.abs(sum));

        if (lowerTail) {
            double f1 = logp ? RMath.log1p(sum) : 1 + sum;
            double f2;
            if (alph > 1) {
                f2 = DPois.dpoisRaw(alph, x, logp);
                f2 = logp ? f2 + x : f2 * Math.exp(x);
            } else if (logp) {
                f2 = alph * Math.log(x) - lgamma1p(alph);
            } else {
                f2 = Math.pow(x, alph) / Math.exp(lgamma1p(alph));
            }
            return logp ? f1 + f2 : f1 * f2;
        } else {
            double lf2 = alph * Math.log(x) - lgamma1p(alph);
            if (logp) {
                return rlog1exp(RMath.log1p(sum) + lf2);
            } else {
                double f2m1 = RMath.expm1(lf2);
                return -(sum + f2m1 + sum * f2m1);
            }
        }
    } /* pgamma_smallx() */

    private static double pdUpperSeries(double x, double y, boolean logp) {
        double localY = y;
        double term = x / localY;
        double sum = term;

        do {
            localY++;
            term *= x / localY;
            sum += term;
        } while (term > sum * DBL_EPSILON);

        /*
         * sum = \sum_{n=1}^ oo x^n / (y*(y+1)*...*(y+n-1)) = \sum_{n=0}^ oo x^(n+1) /
         * (y*(y+1)*...*(y+n)) = x/y * (1 + \sum_{n=1}^oo x^n / ((y+1)*...*(y+n))) ~ x/y + o(x/y)
         * {which happens when alph -> Inf}
         */
        return logp ? Math.log(sum) : sum;
    }

    private static final int max_it = 200000;

    /* Scalefactor:= (2^32)^8 = 2^256 = 1.157921e+77 */
    private static double sqr(double x) {
        return x * x;
    }

    private static final double scalefactor = sqr(sqr(sqr(4294967296.0)));

    /*
     * Continued fraction for calculation of scaled upper-tail F_{gamma} ~= (y / d) * [1 + (1-y)/d +
     * O( ((1-y)/d)^2 ) ]
     */
    private static double pdLowerCf(double y, double d) {
        double f = 0.0 /* -Wall */;
        double of;
        double f0;
        double i;
        double c2;
        double c3;
        double c4;
        double a1;
        double b1;
        double a2;
        double b2;

        if (y == 0) {
            return 0;
        }

        f0 = y / d;
        /* Needed, e.g. for pgamma(10^c(100,295), shape= 1.1, log=TRUE): */
        if (Math.abs(y - 1) < Math.abs(d) * DBL_EPSILON) { /* includes y < d = Inf */
            return f0;
        }

        if (f0 > 1.) {
            f0 = 1.;
        }
        c2 = y;
        c4 = d; /* original (y,d), *not* potentially scaled ones! */

        a1 = 0;
        b1 = 1;
        a2 = y;
        b2 = d;

        while (b2 > scalefactor) {
            a1 /= scalefactor;
            b1 /= scalefactor;
            a2 /= scalefactor;
            b2 /= scalefactor;
        }

        i = 0;
        of = -1.; /* far away */
        while (i < max_it) {
            i++;
            c2--;
            c3 = i * c2;
            c4 += 2;
            /* c2 = y - i, c3 = i(y - i), c4 = d + 2i, for i odd */
            a1 = c4 * a2 + c3 * a1;
            b1 = c4 * b2 + c3 * b1;

            i++;
            c2--;
            c3 = i * c2;
            c4 += 2;
            /* c2 = y - i, c3 = i(y - i), c4 = d + 2i, for i even */
            a2 = c4 * a1 + c3 * a2;
            b2 = c4 * b1 + c3 * b2;

            if (b2 > scalefactor) {
                a1 /= scalefactor;
                b1 /= scalefactor;
                a2 /= scalefactor;
                b2 /= scalefactor;
            }

            if (b2 != 0) {
                f = a2 / b2;
                /* convergence check: relative; "absolute" for very small f : */
                if (Math.abs(f - of) <= DBL_EPSILON * RMath.fmax2(f0, Math.abs(f))) {
                    return f;
                }
                of = f;
            }
        }

        RMathError.warning(Message.GENERIC, Utils.stringFormat(" ** NON-convergence in pgamma()'s pd_lower_cf() f= %g.", f));
        return f; /* should not happen ... */
    } /* pd_lower_cf() */

    private static double pdLowerSeries(double lambda, double y) {
        double localY = y;
        double term = 1;
        double sum = 0;

        while (localY >= 1 && term > sum * DBL_EPSILON) {
            term *= localY / lambda;
            sum += term;
            localY--;
        }
        /*
         * sum = \sum_{n=0}^ oo y*(y-1)*...*(y - n) / lambda^(n+1) = y/lambda * (1 + \sum_{n=1}^Inf
         * (y-1)*...*(y-n) / lambda^n) ~ y/lambda + o(y/lambda)
         */

        if (!Utils.identityEquals(localY, Math.floor(localY))) {
            /*
             * The series does not converge as the terms start getting bigger (besides flipping
             * sign) for y < -lambda.
             */
            double f;
            /*
             * FIXME: in quite few cases, adding term*f has no effect (f too small) and is
             * unnecessary e.g. for pgamma(4e12, 121.1)
             */
            f = pdLowerCf(localY, lambda + 1 - localY);
            sum += term * f;
        }

        return sum;
    } /* pd_lower_series() */

    /*
     * Compute the following ratio with higher accuracy that would be had from doing it directly.
     *
     * dnorm (x, 0, 1, FALSE) ---------------------------------- pnorm (x, 0, 1, lower_tail, FALSE)
     *
     * Abramowitz & Stegun 26.2.12
     */
    private static double dpnorm(double x, boolean lowerTail, double lp) {
        /*
         * So as not to repeat a pnorm call, we expect
         *
         * lp == pnorm (x, 0, 1, lower_tail, TRUE)
         *
         * but use it only in the non-critical case where either x is small or p==exp(lp) is close
         * to 1.
         */
        double localX = x;
        boolean localLowerTail = lowerTail;

        if (localX < 0) {
            localX = -localX;
            localLowerTail = !localLowerTail;
        }

        if (localX > 10 && !localLowerTail) {
            double term = 1 / localX;
            double sum = term;
            double x2 = localX * localX;
            double i = 1;

            do {
                term *= -i / x2;
                sum += term;
                i += 2;
            } while (Math.abs(term) > DBL_EPSILON * sum);

            return 1 / sum;
        } else {
            double d = new DNorm().evaluate(localX, 0., 1., false);
            return d / Math.exp(lp);
        }
    }

    @CompilationFinal(dimensions = 1) private static final double[] coefs_a = new double[]{-1e99, /*
                                                                                                   * placeholder
                                                                                                   * used
                                                                                                   * for
                                                                                                   * 1
                                                                                                   * -
                                                                                                   * indexing
                                                                                                   */
                    2 / 3., -4 / 135., 8 / 2835., 16 / 8505., -8992 / 12629925., -334144 / 492567075., 698752 / 1477701225.};

    @CompilationFinal(dimensions = 1) private static final double[] coefs_b = new double[]{-1e99, /*
                                                                                                   * placeholder
                                                                                                   */
                    1 / 12., 1 / 288., -139 / 51840., -571 / 2488320., 163879 / 209018880., 5246819 / 75246796800., -534703531 / 902961561600.};

    /*
     * Asymptotic expansion to calculate the probability that Poisson variate has value <= x.
     * Various assertions about this are made (without proof) at
     * http://members.aol.com/iandjmsmith/PoissonApprox.htm
     */
    private static double ppoisAsymp(double x, double lambda, boolean lowerTail, boolean logp) {
        double elfb;
        double elfbTerm;
        double res12;
        double res1Term;
        double res1Ig;
        double res2Term;
        double res2Ig;
        double dfm;
        double ptu;
        double s2pt;
        double f;
        double np;
        int i;

        dfm = lambda - x;
        /*
         * If lambda is large, the distribution is highly concentrated about lambda. So
         * representation error in x or lambda can lead to arbitrarily large values of ptu and hence
         * divergence of the coefficients of this approximation.
         */
        ptu = -log1pmx(dfm / x);
        s2pt = Math.sqrt(2 * x * ptu);
        if (dfm < 0) {
            s2pt = -s2pt;
        }

        res12 = 0;
        res1Ig = res1Term = Math.sqrt(x);
        res2Ig = res2Term = s2pt;
        for (i = 1; i < 8; i++) {
            res12 += res1Ig * coefs_a[i];
            res12 += res2Ig * coefs_b[i];
            res1Term *= ptu / i;
            res2Term *= 2 * ptu / (2 * i + 1);
            res1Ig = res1Ig / x + res1Term;
            res2Ig = res2Ig / x + res2Term;
        }

        elfb = x;
        elfbTerm = 1;
        for (i = 1; i < 8; i++) {
            elfb += elfbTerm * coefs_b[i];
            elfbTerm /= x;
        }
        if (!lowerTail) {
            elfb = -elfb;
        }
        f = res12 / elfb;

        np = Pnorm.createTemp().evaluate(s2pt, 0.0, 1.0, !lowerTail, logp);

        if (logp) {
            double ndOverP = dpnorm(s2pt, !lowerTail, np);
            return np + RMath.log1p(f * ndOverP);
        } else {
            double nd = new DNorm().evaluate(s2pt, 0., 1., logp);
            return np + f * nd;
        }
    } /* ppois_asymp() */

    public static double pgammaRaw(double x, double alph, boolean lowerTail, boolean logp) {
        /* Here, assume that (x,alph) are not NA & alph > 0 . */

        double res;

        try {
            DPQ.rpbounds01(x, 0., Double.POSITIVE_INFINITY, lowerTail, logp);
        } catch (EarlyReturn e) {
            return e.result;
        }

        if (x < 1) {
            res = pgammaSmallx(x, alph, lowerTail, logp);
        } else if (x <= alph - 1 && x < 0.8 * (alph + 50)) {
            /* incl. large alph compared to x */
            double sum = pdUpperSeries(x, alph, logp); /* = x/alph + o(x/alph) */
            double d = dpoisWrap(alph, x, logp);
            if (!lowerTail) {
                res = logp ? rlog1exp(d + sum) : 1 - d * sum;
            } else {
                res = logp ? sum + d : sum * d;
            }
        } else if (alph - 1 < x && alph < 0.8 * (x + 50)) {
            /* incl. large x compared to alph */
            double sum;
            double d = dpoisWrap(alph, x, logp);
            if (alph < 1) {
                if (x * DBL_EPSILON > 1 - alph) {
                    sum = rd1(logp);
                } else {
                    double f = pdLowerCf(alph, x - (alph - 1)) * x / alph;
                    /* = [alph/(x - alph+1) + o(alph/(x-alph+1))] * x/alph = 1 + o(1) */
                    sum = logp ? Math.log(f) : f;
                }
            } else {
                sum = pdLowerSeries(x, alph - 1); /* = (alph-1)/x + o((alph-1)/x) */
                sum = logp ? RMath.log1p(sum) : 1 + sum;
            }
            if (!lowerTail) {
                res = logp ? sum + d : sum * d;
            } else {
                res = logp ? rlog1exp(d + sum) : 1 - d * sum;
            }
        } else { /* x >= 1 and x fairly near alph. */
            res = ppoisAsymp(alph - 1, x, !lowerTail, logp);
        }

        /*
         * We lose a fair amount of accuracy to underflow in the cases where the final result is
         * very close to DBL_MIN. In those cases, simply redo via log space.
         */
        if (!logp && res < DBL_MIN / DBL_EPSILON) {
            /* with(.Machine, double.xmin / double.eps) #|-> 1.002084e-292 */
            return Math.exp(pgammaRaw(x, alph, lowerTail, true));
        } else {
            return res;
        }
    }

    public static double pgamma(double x, double alph, double scale, boolean lowerTail, boolean logp) {
        double localX = x;
        if (Double.isNaN(localX) || Double.isNaN(alph) || Double.isNaN(scale)) {
            return localX + alph + scale;
        }
        if (alph < 0. || scale <= 0.) {
            return RMathError.defaultError();
        }
        localX /= scale;
        if (Double.isNaN(localX)) { /* eg. original x = scale = +Inf */
            return localX;
        }
        if (alph == 0.) { /* limit case; useful e.g. in pnchisq() */
            // assert pgamma(0,0) ==> 0
            return (localX <= 0) ? rdt0(lowerTail, logp) : rdt1(lowerTail, logp);
        }
        return pgammaRaw(localX, alph, lowerTail, logp);
    }

    //
    // dgamma
    //

    public static double dgamma(double x, double shape, double scale, boolean giveLog) {
        double pr;
        if (Double.isNaN(x) || Double.isNaN(shape) || Double.isNaN(scale)) {
            return x + shape + scale;
        }
        if (shape < 0 || scale <= 0) {
            return RMathError.defaultError();
        }
        if (x < 0) {
            return rd0(giveLog);
        }
        if (shape == 0) { /* point mass at 0 */
            return (x == 0) ? Double.POSITIVE_INFINITY : rd0(giveLog);
        }
        if (x == 0) {
            if (shape < 1) {
                return Double.POSITIVE_INFINITY;
            }
            if (shape > 1) {
                return rd0(giveLog);
            }
            /* else */
            return giveLog ? -Math.log(scale) : 1 / scale;
        }

        if (shape < 1) {
            pr = DPois.dpoisRaw(shape, x / scale, giveLog);
            return giveLog ? pr + Math.log(shape / x) : pr * shape / x;
        }
        /* else shape >= 1 */
        pr = DPois.dpoisRaw(shape - 1, x / scale, giveLog);
        return giveLog ? pr - Math.log(scale) : pr / scale;
    }

    // the following is transcribed from polygamma.c

    @CompilationFinal(dimensions = 1) private static final double[] bvalues = new double[]{1.00000000000000000e+00, -5.00000000000000000e-01, 1.66666666666666667e-01, -3.33333333333333333e-02,
                    2.38095238095238095e-02, -3.33333333333333333e-02, 7.57575757575757576e-02, -2.53113553113553114e-01, 1.16666666666666667e+00, -7.09215686274509804e+00,
                    5.49711779448621554e+01, -5.29124242424242424e+02, 6.19212318840579710e+03, -8.65802531135531136e+04, 1.42551716666666667e+06, -2.72982310678160920e+07,
                    6.01580873900642368e+08, -1.51163157670921569e+10, 4.29614643061166667e+11, -1.37116552050883328e+13, 4.88332318973593167e+14, -1.92965793419400681e+16};

    private static final int n_max = 100;
    // the following is actually a parameter in the original code, but it's always 1 and must be
    // as the original code treats the "ans" value of type double as an array, which is legal
    // only if a the first element of the array is accessed at all times
    private static final int m = 1;

    public static final class DpsiFnResult {

        public double ans;
        public int nz;
        public int ierr;

        DpsiFnResult(double ans) {
            this.ans = ans;
        }

        DpsiFnResult(double ans, int nz, int ierr) {
            this.ans = ans;
            this.nz = nz;
            this.ierr = ierr;
        }
    }

    public static DpsiFnResult dpsifn(double x, int n, int kode, double ans) {
        DpsiFnResult result = new DpsiFnResult(ans);
        dpsifn(x, n, kode, result);
        return result;
    }

    public static DpsiFnResult dpsifn(double x, int n, int kode, double ans, int nz, int ierr) {
        DpsiFnResult result = new DpsiFnResult(ans, nz, ierr);
        dpsifn(x, n, kode, result);
        return result;
    }

    @TruffleBoundary
    @SuppressWarnings("all")
    @SuppressFBWarnings(value = "UC_USELESS_CONDITION", justification = "taken from original implementation")
    private static void dpsifn(double x, int n, int kode, DpsiFnResult result) {
        int mm;
        int mx;
        int nn;
        int np;
        int nx;
        int fn;
        double arg;
        double den;
        double elim;
        double eps;
        double fln;
        double rln;
        double r1m4;
        double r1m5;
        double s;
        double slope;
        double t;
        double tk;
        double tt;
        double t1;
        double t2;
        double wdtol;
        double xdmln;
        double xdmy;
        double xinc;
        double xln = 0.0;
        double xm;
        double xmin;
        double yint;
        double[] trm = new double[23];
        double[] trmr = new double[n_max + 1];

        // non-zero ierr always results in generating a NaN
        result.ierr = 0;
        if (n < 0 || kode < 1 || kode > 2 || m < 1) {
            result.ans = Double.NaN;
            return;
        }
        if (x <= 0.) {
            /*
             * use Abramowitz & Stegun 6.4.7 "Reflection Formula" psi(k, x) = (-1)^k psi(k, 1-x) -
             * pi^{n+1} (d/dx)^n cot(x)
             */
            if (x == RMath.round(x)) {
                /* non-positive integer : +Inf or NaN depends on n */
                // for(j=0; j < m; j++) /* k = j + n : */
                // ans[j] = ((j+n) % 2) ? ML_POSINF : ML_NAN;
                // m is always 1
                result.ans = (n % 2) != 0 ? ML_POSINF : ML_NAN;
                return;
            }
            /* This could cancel badly */
            dpsifn(1. - x, n, /* kode = */1, result);
            /*
             * ans[j] == (-1)^(k+1) / gamma(k+1) * psi(k, 1 - x) for j = 0:(m-1) , k = n + j
             */

            /* Cheat for now: only work for m = 1, n in {0,1,2,3} : */
            if (m > 1 || n > 3) { /* doesn't happen for digamma() .. pentagamma() */
                /* not yet implemented */
                // non-zero ierr always results in generating a NaN
                result.ierr = 4;
                result.ans = Double.NaN;
                return;
            }
            x *= M_PI; /* pi * x */
            if (n == 0) {
                tt = Math.cos(x) / Math.sin(x);
            } else if (n == 1) {
                tt = -1 / Math.pow(Math.sin(x), 2);
            } else if (n == 2) {
                tt = 2 * Math.cos(x) / Math.pow(Math.sin(x), 3);
            } else if (n == 3) {
                tt = -2 * (2 * Math.pow(Math.cos(x), 2) + 1.) / Math.pow(Math.sin(x), 4);
            } else { /* can not happen! */
                tt = RRuntime.DOUBLE_NA;
            }
            /* end cheat */

            s = (n % 2) != 0 ? -1. : 1.; /* s = (-1)^n */
            /*
             * t := pi^(n+1) * d_n(x) / gamma(n+1) , where d_n(x) := (d/dx)^n cot(x)
             */
            t1 = t2 = s = 1.;
            for (int k = 0, j = k - n; j < m; k++, j++, s = -s) {
                /* k == n+j , s = (-1)^k */
                t1 *= M_PI; /* t1 == pi^(k+1) */
                if (k >= 2) {
                    t2 *= k; /* t2 == k! == gamma(k+1) */
                }
                if (j >= 0) { /* by cheat above, tt === d_k(x) */
                    // j must always be 0
                    assert j == 0;
                    // ans[j] = s*(ans[j] + t1/t2 * tt);
                    result.ans = s * (result.ans + t1 / t2 * tt);
                }
            }
            if (n == 0 && kode == 2) { /* unused from R, but "wrong": xln === 0 : */
                // ans[0] += xln;
                result.ans += xln;
            }
            return;
        } /* x <= 0 */

        /* else : x > 0 */
        // nz not used
        result.nz = 0;
        xln = Math.log(x);
        if (kode == 1 /* && m == 1 */) { /* the R case --- for very large x: */
            double lrg = 1 / (2. * DBL_EPSILON);
            if (n == 0 && x * xln > lrg) {
                // ans[0] = -xln;
                result.ans = -xln;
                return;
            } else if (n >= 1 && x > n * lrg) {
                // ans[0] = exp(-n * xln)/n; /* == x^-n / n == 1/(n * x^n) */
                result.ans = Math.exp(-n * xln) / n;
                return;
            }
        }
        mm = m;
        // nx = imin2(-Rf_i1mach(15), Rf_i1mach(16));/* = 1021 */
        nx = Math.min(-DBL_MIN_EXP, DBL_MAX_EXP);
        assert (nx == 1021);
        r1m5 = M_LOG10_2; // Rf_d1mach(5);
        r1m4 = DBL_EPSILON * 0.5; // Rf_d1mach(4) * 0.5;
        wdtol = fmax2(r1m4, 0.5e-18); /* 1.11e-16 */

        /* elim = approximate exponential over and underflow limit */
        elim = 2.302 * (nx * r1m5 - 3.0); /* = 700.6174... */
        for (;;) {
            nn = n + mm - 1;
            fn = nn;
            t = (fn + 1) * xln;

            /* overflow and underflow test for small and large x */

            if (Math.abs(t) > elim) {
                if (t <= 0.0) {
                    // nz not used
                    result.nz = 0;
                    // non-zero ierr always results in generating a NaN
                    result.ierr = 2;
                    result.ans = Double.NaN;
                    return;
                }
            } else {
                if (x < wdtol) {
                    // ans[0] = R_pow_di(x, -n-1);
                    result.ans = Math.pow(x, -n - 1);
                    if (mm != 1) {
                        // for(k = 1; k < mm ; k++)
                        // ans[k] = ans[k-1] / x;
                        assert mm < 2;
                        // int the original code, ans should not be accessed beyond the 0th
                        // index
                    }
                    if (n == 0 && kode == 2) {
                        // ans[0] += xln;
                        result.ans += xln;
                    }
                    return;
                }

                /* compute xmin and the number of terms of the series, fln+1 */

                rln = r1m5 * DBL_MANT_DIG; // Rf_i1mach(14);
                rln = Math.min(rln, 18.06);
                fln = Math.max(rln, 3.0) - 3.0;
                yint = 3.50 + 0.40 * fln;
                slope = 0.21 + fln * (0.0006038 * fln + 0.008677);
                xm = yint + slope * fn;
                mx = (int) xm + 1;
                xmin = mx;
                if (n != 0) {
                    xm = -2.302 * rln - Math.min(0.0, xln);
                    arg = xm / n;
                    arg = Math.min(0.0, arg);
                    eps = Math.exp(arg);
                    xm = 1.0 - eps;
                    if (Math.abs(arg) < 1.0e-3) {
                        xm = -arg;
                    }
                    fln = x * xm / eps;
                    xm = xmin - x;
                    if (xm > 7.0 && fln < 15.0) {
                        break;
                    }
                }
                xdmy = x;
                xdmln = xln;
                xinc = 0.0;
                if (x < xmin) {
                    nx = (int) x;
                    xinc = xmin - nx;
                    xdmy = x + xinc;
                    xdmln = Math.log(xdmy);
                }

                /* generate w(n+mm-1, x) by the asymptotic expansion */

                t = fn * xdmln;
                t1 = xdmln + xdmln;
                t2 = t + xdmln;
                tk = Math.max(Math.abs(t), fmax2(Math.abs(t1), Math.abs(t2)));
                if (tk <= elim) { /* for all but large x */
                    l10(t, tk, xdmy, xdmln, x, nn, nx, wdtol, fn, trm, trmr, xinc, mm, kode, result);
                    return;
                }
            }
            // nz not used
            result.nz++; /* underflow */
            mm--;
            // ans[mm] = 0.;
            assert mm == 0;
            result.ans = 0.;
            if (mm == 0) {
                return;
            }
        } /* end{for()} */
        nn = (int) fln + 1;
        np = n + 1;
        t1 = (n + 1) * xln;
        t = Math.exp(-t1);
        s = t;
        den = x;
        for (int i = 1; i <= nn; i++) {
            den += 1.;
            trm[i] = Math.pow(den, -np);
            s += trm[i];
        }
        // ans[0] = s;
        result.ans = s;
        if (n == 0 && kode == 2) {
            // ans[0] = s + xln;
            result.ans = s + xln;
        }

        if (mm != 1) { /* generate higher derivatives, j > n */
            assert false;
            // tol = wdtol / 5.0;
            // for(j = 1; j < mm; j++) {
            // t /= x;
            // s = t;
            // tols = t * tol;
            // den = x;
            // for(i=1; i <= nn; i++) {
            // den += 1.;
            // trm[i] /= den;
            // s += trm[i];
            // if (trm[i] < tols) {
            // break;
            // }
            // }
            // ans[j] = s;
            // }
        }
    }

    private static void l10(double oldT, double oldTk, double xdmy, double xdmln, double x, double nn, double oldNx, double wdtol, double oldFn, double[] trm, double[] trmr, double xinc,
                    double mm, int kode, DpsiFnResult result) {
        double t = oldT;
        double tk = oldTk;
        double nx = oldNx;
        double fn = oldFn;

        double tss = Math.exp(-t);
        double tt = 0.5 / xdmy;
        double t1 = tt;
        double tst = wdtol * tt;
        if (nn != 0) {
            t1 = tt + 1.0 / fn;
        }
        double rxsq = 1.0 / (xdmy * xdmy);
        double ta = 0.5 * rxsq;
        t = (fn + 1) * ta;
        double s = t * bvalues[2];
        if (Math.abs(s) >= tst) {
            tk = 2.0;
            for (int k = 4; k <= 22; k++) {
                t = t * ((tk + fn + 1) / (tk + 1.0)) * ((tk + fn) / (tk + 2.0)) * rxsq;
                trm[k] = t * bvalues[k - 1];
                if (Math.abs(trm[k]) < tst) {
                    break;
                }
                s += trm[k];
                tk += 2.;
            }
        }
        s = (s + t1) * tss;
        if (xinc != 0.0) {

            /* backward recur from xdmy to x */

            nx = (int) xinc;
            double np = nn + 1;
            if (nx > n_max) {
                // nz not used
                result.nz = 0;
                // non-zero ierr always results in generating a NaN
                result.ierr = 3;
                result.ans = Double.NaN;
                return;
            } else {
                if (nn == 0) {
                    l20(xdmln, xdmy, x, s, nx, kode, result);
                    return;
                }
                double xm = xinc - 1.0;
                double fx = x + xm;

                /* this loop should not be changed. fx is accurate when x is small */
                for (int i = 1; i <= nx; i++) {
                    trmr[i] = Math.pow(fx, -np);
                    s += trmr[i];
                    xm -= 1.;
                    fx = x + xm;
                }
            }
        }
        // ans[mm-1] = s;
        assert (mm - 1) == 0;
        result.ans = s;
        if (fn == 0) {
            l30(xdmln, xdmy, x, s, kode, result);
            return;
        }

        /* generate lower derivatives, j < n+mm-1 */

        for (int j = 2; j <= mm; j++) {
            fn--;
            tss *= xdmy;
            t1 = tt;
            if (fn != 0) {
                t1 = tt + 1.0 / fn;
            }
            t = (fn + 1) * ta;
            s = t * bvalues[2];
            if (Math.abs(s) >= tst) {
                tk = 4 + fn;
                for (int k = 4; k <= 22; k++) {
                    trm[k] = trm[k] * (fn + 1) / tk;
                    if (Math.abs(trm[k]) < tst) {
                        break;
                    }
                    s += trm[k];
                    tk += 2.;
                }
            }
            s = (s + t1) * tss;
            if (xinc != 0.0) {
                if (fn == 0) {
                    l20(xdmln, xdmy, x, s, nx, kode, result);
                    return;
                }
                double xm = xinc - 1.0;
                double fx = x + xm;
                for (int i = 1; i <= nx; i++) {
                    trmr[i] = trmr[i] * fx;
                    s += trmr[i];
                    xm -= 1.;
                    fx = x + xm;
                }
            }
            // ans[mm - j] = s;
            assert (mm - j) == 0;
            result.ans = s;
            if (fn == 0) {
                l30(xdmln, xdmy, x, s, kode, result);
                return;
            }
        }

    }

    private static void l20(double xdmln, double xdmy, double x, double oldS, double nx, int kode, DpsiFnResult result) {
        double s = oldS;
        for (int i = 1; i <= nx; i++) {
            s += 1. / (x + (nx - i)); /* avoid disastrous cancellation, PR#13714 */
        }

        l30(xdmln, xdmy, x, s, kode, result);
    }

    private static void l30(double xdmln, double xdmy, double x, double s, int kode, DpsiFnResult result) {
        if (kode != 2) { /* always */
            // ans[0] = s - xdmln;
            result.ans = s - xdmln;
        } else if (xdmy != x) {
            double xq;
            xq = xdmy / x;
            // ans[0] = s - log(xq);
            result.ans = s - Math.log(xq);
        }
    }

    @SuppressWarnings("all")
    public static double psigamma(double x, double deriv) {
        // polygamma.c
        /* n-th derivative of psi(x); e.g., psigamma(x,0) == digamma(x) */
        if (Double.isNaN(x)) {
            return x;
        }
        deriv = RMath.forceint(deriv);
        int n = (int) deriv;
        if (n > n_max) {
            RMathError.warning(RError.Message.DERIV_OVER_N_MAX, n, n_max);
            return ML_NAN;
        }
        DpsiFnResult result = dpsifn(x, n, 1, 0);
        // ML_TREAT_psigam(ierr);
        /* Now, ans == A := (-1)^(n+1) / gamma(n+1) * psi(n, x) */
        result.ans = -result.ans; /* = (-1)^(0+1) * gamma(0+1) * A */
        for (int k = 1; k <= n; k++) {
            result.ans *= (-k); /* = (-1)^(k+1) * gamma(k+1) * A */
        }
        return result.ans; /* = psi(n, x) */
    }

    public static double digamma(double x) {
        return -dpsifn(x, 0, 1, 0).ans;
    }

    public static double trigamma(double x) {
        return dpsifn(x, 1, 1, 1).ans;
    }

    public static double tetragamma(double x) {
        return -2.0 * dpsifn(x, 2, 1, 1).ans;
    }

    public static double pentagamma(double x) {
        return 6.0 * dpsifn(x, 3, 1, 1).ans;
    }

}
