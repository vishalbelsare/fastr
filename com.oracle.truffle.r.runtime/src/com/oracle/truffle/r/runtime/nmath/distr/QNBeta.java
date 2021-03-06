/*
 * Copyright (c) 2006, The R Core Team
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.R-project.org/Licenses/
 */
package com.oracle.truffle.r.runtime.nmath.distr;

import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_EPSILON;
import static com.oracle.truffle.r.runtime.nmath.MathConstants.DBL_MIN;

import com.oracle.truffle.r.runtime.nmath.DPQ;
import com.oracle.truffle.r.runtime.nmath.DPQ.EarlyReturn;
import com.oracle.truffle.r.runtime.nmath.MathFunctions.Function4_2;
import com.oracle.truffle.r.runtime.nmath.RMath;
import com.oracle.truffle.r.runtime.nmath.RMathError;

public final class QNBeta implements Function4_2 {

    public static QNBeta create() {
        return new QNBeta();
    }

    public static QNBeta getUncached() {
        return new QNBeta();
    }

    private static final double accu = 1e-15;
    private static final double Eps = 1e-14; /* must be > accu */

    private final PNBeta pnbeta = new PNBeta();

    @Override
    public double evaluate(double pIn, double a, double b, double ncp, boolean lowerTail, boolean logP) {
        if (Double.isNaN(pIn) || Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(ncp)) {
            return pIn + a + b + ncp;
        }
        if (!Double.isFinite(a)) {
            return RMathError.defaultError();
        }

        if (ncp < 0. || a <= 0. || b <= 0.) {
            return RMathError.defaultError();
        }

        try {
            DPQ.rqp01boundaries(pIn, 0, 1, lowerTail, logP);
        } catch (EarlyReturn e) {
            return e.result;
        }

        double p = DPQ.rdtqiv(pIn, lowerTail, logP);

        /*
         * Invert pnbeta(.) : 1. finding an upper and lower bound
         */
        if (p > 1 - DBL_EPSILON) {
            return 1.0;
        }
        double pp = RMath.fmin2(1 - DBL_EPSILON, p * (1 + Eps));
        double ux = 0.5;
        while (ux < 1 - DBL_EPSILON && pnbeta.evaluate(ux, a, b, ncp, true, false) < pp) {
            ux = 0.5 * (1 + ux);
        }
        pp = p * (1 - Eps);
        double lx = 0.5;
        while (lx > DBL_MIN && pnbeta.evaluate(lx, a, b, ncp, true, false) > pp) {
            lx *= 0.5;
        }

        /* 2. interval (lx,ux) halving : */
        double nx;
        do {
            nx = 0.5 * (lx + ux);
            if (pnbeta.evaluate(nx, a, b, ncp, true, false) > p) {
                ux = nx;
            } else {
                lx = nx;
            }
        } while ((ux - lx) / nx > accu);

        return 0.5 * (ux + lx);
    }
}
