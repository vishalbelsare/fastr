/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.test.test;

import static com.oracle.truffle.r.runtime.data.RDataFactory.createComplexVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createDoubleSequence;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createDoubleVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyComplexVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyDoubleVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyIntVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createEmptyLogicalVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createIntSequence;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createIntVector;
import static com.oracle.truffle.r.runtime.data.RDataFactory.createLogicalVector;

import com.oracle.truffle.r.runtime.data.RDataFactory;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.experimental.theories.DataPoint;

import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class BinaryVectorTest extends TestBase {

    @DataPoint public static final RAbstractVector PRIMITIVE_LOGICAL = RDataFactory.createLogicalVectorFromScalar((byte) 1);
    @DataPoint public static final RAbstractVector PRIMITIVE_INTEGER = RDataFactory.createIntVectorFromScalar(42);
    @DataPoint public static final RAbstractVector PRIMITIVE_DOUBLE = RDataFactory.createDoubleVectorFromScalar(42d);
    @DataPoint public static final RAbstractVector PRIMITIVE_COMPLEX = RDataFactory.createComplexVectorFromScalar(1.0, 1.0);

    @DataPoint public static final RAbstractVector EMPTY_LOGICAL = createEmptyLogicalVector();
    @DataPoint public static final RAbstractVector EMPTY_INTEGER = createEmptyIntVector();
    @DataPoint public static final RAbstractVector EMPTY_DOUBLE = createEmptyDoubleVector();
    @DataPoint public static final RAbstractVector EMPTY_COMPLEX = createEmptyComplexVector();

    @DataPoint public static final RAbstractVector SEQUENCE_INT = createIntSequence(1, 2, 10);
    @DataPoint public static final RAbstractVector SEQUENCE_DOUBLE = createDoubleSequence(1, 2, 10);

    @DataPoint public static final RAbstractVector FOUR_LOGICAL = createLogicalVector(new byte[]{1, 0, 1, 0}, true);
    @DataPoint public static final RAbstractVector FOUR_INT = createIntVector(new int[]{1, 2, 3, 4}, true);
    @DataPoint public static final RAbstractVector FOUR_DOUBLE = createDoubleVector(new double[]{1, 2, 3, 4}, true);
    @DataPoint public static final RAbstractVector FOUR_COMPLEX = createComplexVector(new double[]{1, 1, 2, 2, 3, 3, 4, 4}, true);

    @DataPoint public static final RAbstractVector NOT_COMPLETE_LOGICAL = createLogicalVector(new byte[]{1, 0, RRuntime.LOGICAL_NA, 1}, false);
    @DataPoint public static final RAbstractVector NOT_COMPLETE_INT = createIntVector(new int[]{1, 2, RRuntime.INT_NA, 4}, false);
    @DataPoint public static final RAbstractVector NOT_COMPLETE_DOUBLE = createDoubleVector(new double[]{1, 2, RRuntime.DOUBLE_NA, 4}, false);
    @DataPoint public static final RAbstractVector NOT_COMPLETE_COMPLEX = createComplexVector(new double[]{1.0d, 0.0d, RRuntime.COMPLEX_NA_REAL_PART, RRuntime.COMPLEX_NA_IMAGINARY_PART}, false);

    @DataPoint public static final RAbstractVector ONE = createIntVector(new int[]{1}, true);
    @DataPoint public static final RAbstractVector TWO = createIntVector(new int[]{1, 2}, true);
    @DataPoint public static final RAbstractVector THREE = createIntVector(new int[]{1, 2, 3}, true);
    @DataPoint public static final RAbstractVector FIVE = createIntVector(new int[]{1, 2, 3, 4, 5}, true);

    /*
     * We keep the fields @DataPoint instead of the ALL_VECTORS field in order to have better error
     * messages.
     */
    public static final RAbstractVector[] ALL_VECTORS = new RAbstractVector[]{PRIMITIVE_LOGICAL, PRIMITIVE_INTEGER, PRIMITIVE_DOUBLE,
                    PRIMITIVE_COMPLEX, EMPTY_LOGICAL, EMPTY_INTEGER, EMPTY_DOUBLE, EMPTY_COMPLEX, SEQUENCE_INT, SEQUENCE_DOUBLE, FOUR_LOGICAL, FOUR_INT,
                    FOUR_COMPLEX, NOT_COMPLETE_LOGICAL, NOT_COMPLETE_INT, NOT_COMPLETE_DOUBLE, NOT_COMPLETE_COMPLEX, ONE, TWO, THREE, FIVE};

    protected Matcher<Object> isEmptyVectorOf(RType type) {
        return new CustomMatcher<>("empty vector of type " + type) {
            @Override
            public boolean matches(Object item) {
                return item instanceof RAbstractVector && ((RAbstractVector) item).getLength() == 0 && ((RAbstractVector) item).getRType() == type;
            }
        };
    }
}
