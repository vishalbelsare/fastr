/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.access.vector;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.r.nodes.access.vector.ElementAccessMode;
import com.oracle.truffle.r.nodes.access.vector.ExtractVectorNode;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNode;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNodeGen;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.test.test.TestBase;
import com.oracle.truffle.r.test.test.TestUtilities;
import com.oracle.truffle.r.test.test.TestUtilities.NodeHandle;

@RunWith(Theories.class)
public class ExtractVectorNodeTest extends TestBase {
    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }

    @DataPoints public static RType[] vectorTypes = RType.getVectorTypes();

    @DataPoint public static RAbstractVector accessEmpty = RDataFactory.createEmptyLogicalVector();
    @DataPoint public static RAbstractVector accessFirst = RDataFactory.createIntVector(new int[]{1}, true);
    @DataPoint public static RAbstractVector accessSecond = RDataFactory.createIntVector(new int[]{2}, true);
    @DataPoint public static RAbstractVector accessEverythingButFirst = RDataFactory.createIntVector(new int[]{-1}, true);
    @DataPoint public static RAbstractVector accessNA = RDataFactory.createIntVector(new int[]{RRuntime.INT_NA}, false);
    @DataPoint public static RAbstractVector accessZero = RDataFactory.createIntVector(new int[]{0}, false);
    @DataPoint public static RAbstractVector accessFirstTwo = RDataFactory.createIntVector(new int[]{1, 2}, true);

    @DataPoint public static RAbstractVector accessPositiveSequence = RDataFactory.createIntSequence(1, 1, 2);
    @DataPoint public static RAbstractVector accessPositiveSequenceStride2 = RDataFactory.createIntSequence(1, 2, 2);
    @DataPoint public static RAbstractVector accessNegativeSequence = RDataFactory.createIntSequence(-1, -1, 2);

    @DataPoints public static ElementAccessMode[] allModes = ElementAccessMode.values();

    @Test
    public void testSubsetMultiDimension() {
        execInContext(() -> {
            RIntVector vector;

            // replace rectangle with rectangle indices
            vector = TestUtilities.generateInteger(20, true);
            vector.setDimensions(new int[]{5, 4});
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            RDataFactory.createIntVector(new int[]{2, 3, 4}, true), RDataFactory.createIntVector(new int[]{2, 3}, true));
            assertIndicies(vector, 6, 7, 8, 11, 12, 13);

            // replace box with box indices
            vector = TestUtilities.generateInteger(9, true);
            vector.setDimensions(new int[]{3, 3});
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            RDataFactory.createIntVector(new int[]{2, 3}, true), RDataFactory.createIntVector(new int[]{2, 3}, true));
            assertIndicies(vector, 4, 5, 7, 8);

            // replace three dimensions
            vector = TestUtilities.generateInteger(24, true);
            vector.setDimensions(new int[]{2, 3, 4});
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2}, true));
            assertIndicies(vector, 9);

            // replace three dimensions
            vector = TestUtilities.generateInteger(24, true);
            vector.setDimensions(new int[]{2, 3, 4});
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2, 3}, true), RDataFactory.createIntVector(new int[]{2, 3, 4}, true));
            assertIndicies(vector, 9, 11, 15, 17, 21, 23);
            return null;
        });
    }

    private static void assertIndicies(RIntVector vector, int... expectedValues) {
        assertThat(vector.getLength(), is(expectedValues.length));

        int[] actual = new int[vector.getLength()];
        for (int i = 0; i < expectedValues.length; i++) {
            actual[i] = vector.getDataAt(i);
        }
        assertThat(actual, is(expectedValues));
    }

    @Test
    public void testSubsetSingleDimension() {
        execInContext(() -> {
            RIntVector vector;

            // extract scalar with logical vector with NA
            vector = TestUtilities.generateInteger(4, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_NA}, false)});
            assertIndicies(vector, 0, RRuntime.INT_NA, 2, RRuntime.INT_NA);

            // extract scalar with sequence stride=1
            vector = TestUtilities.generateInteger(9, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector, new Object[]{RDataFactory.createIntSequence(5, 1, 3)});
            assertIndicies(vector, 4, 5, 6);

            // extract scalar with sequence stride>1
            vector = TestUtilities.generateInteger(9, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector, new Object[]{RDataFactory.createIntSequence(5, 2, 2)});
            assertIndicies(vector, 4, 6);

            // extract scalar with negative integer vector
            vector = TestUtilities.generateInteger(4, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector, new Object[]{RDataFactory.createIntVector(new int[]{-2}, true)});
            assertIndicies(vector, 0, 2, 3);

            // extract scalar with logical scalar
            vector = TestUtilities.generateInteger(3, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE}, true)});
            assertIndicies(vector, 0, 1, 2);

            // extract scalar with integer vector with NA
            vector = TestUtilities.generateInteger(4, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            new Object[]{RDataFactory.createIntVector(new int[]{1, RRuntime.INT_NA}, false)});
            assertIndicies(vector, 0, RRuntime.INT_NA);

            // extract scalar with logical vector
            vector = TestUtilities.generateInteger(4, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_FALSE}, true)});
            assertIndicies(vector, 0, 2);

            // extract vector indexed by logical vector
            vector = TestUtilities.generateInteger(4, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector,
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_FALSE}, true)});
            assertIndicies(vector, 0, 2);

            // extract scalar with integer vector
            vector = TestUtilities.generateInteger(9, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector, new Object[]{RDataFactory.createIntVector(new int[]{9, 8}, true)});
            assertIndicies(vector, 8, 7);

            // extract scalar with integer scalar
            vector = TestUtilities.generateInteger(9, true);
            vector = executeExtract(ElementAccessMode.SUBSET, vector, new Object[]{RDataFactory.createIntVector(new int[]{9}, true)});
            assertIndicies(vector, 8);
            return null;
        });
    }

    @Theory
    public void testNames(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            RStringVector names = (RStringVector) TestUtilities.generateVector(RType.Character, 4, true);
            vector.setNames(names);
            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(2));

            RStringVector newNames = result.getNames();
            assertThat(newNames.getLength(), is(1));
            assertThat(newNames.getDataAt(0), is(names.getDataAt(1)));
            return null;
        });
    }

    @Theory
    public void testOutOfBoundsAccess(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(5));

            assertThat(vector.getRType(), is(result.getRType()));
            assertThat(result.getLength(), is(1));
            Object expectedValue = targetType.create(1, true).getDataAtAsObject(0);
            assertThat(result.getDataAtAsObject(0), is(expectedValue));
            return null;
        });
    }

    @Theory
    public void testCompletenessOutOfBounds(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            assumeTrue(targetType != RType.Raw);

            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(10));

            assertThat(result.isComplete(), is(false));
            return null;
        });
    }

    @Theory
    public void testCompletenessAfterScalarExtraction(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, false);

            assumeTrue(targetType != RType.List);
            assumeThat(vector.isComplete(), is(false));
            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(1));

            // TODO failing - how comes?
            assertThat(result.isComplete(), is(true));
            return null;
        });
    }

    @Theory
    public void testCompletenessAfterExtraction(RType targetType) {
        execInContext(() -> {
            assumeTrue(isCacheEnabled());
            assumeTrue(!VectorDataLibrary.ENABLE_VERY_SLOW_ASSERTS);
            assumeTrue(targetType != RType.List);

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, false);

            assumeThat(vector.isComplete(), is(false));
            // extract some non NA elements
            int[] positions = targetType == RType.Complex ? new int[]{1, 3} : new int[]{1, 2};
            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVector(positions, true));

            assertThat(result.isComplete(), is(true));
            return null;
        });
    }

    @Theory
    public void testCompletenessAfterSelectAll(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, false);

            assumeThat(vector.isComplete(), is(false));
            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RDataFactory.createLogicalVectorFromScalar(true));

            assertThat(result.isComplete(), is(false));
            return null;
        });
    }

    @Theory
    public void testCompletenessPositionNA(RType targetType) {
        execInContext(() -> {
            assumeTrue(targetType != RType.Raw);

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            RAbstractVector result = executeExtract(ElementAccessMode.SUBSET, vector, RRuntime.LOGICAL_NA);

            assertThat(result.isComplete(), is(false));
            return null;
        });
    }

    @Theory
    public void testSubsetSingleDimensionTheory(RType targetType, RAbstractVector position) {
        execInContext(() -> {
            assumeTrue(position.getLength() <= 4);
            assumeTrue(position.getLength() >= 1);

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            executeExtract(ElementAccessMode.SUBSET, vector, position);
            return null;
        });
    }

    @Theory
    public void testSubscriptSingleDimensionTheory(RType targetType, RAbstractVector position) {
        execInContext(() -> {
            assumeTrue(position.getLength() == 1);
            if (position instanceof RIntVector) {
                assumeTrue(((RIntVector) position).getDataAt(0) > 0);
            }

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);

            executeExtract(ElementAccessMode.SUBSCRIPT, vector, position);
            return null;
        });
    }

    private NodeHandle<ExtractVectorNode> handle;
    private NodeHandle<BoxPrimitiveNode> box;
    private ElementAccessMode currentMode;
    private boolean currentExact;
    private boolean currentDropDimension;

    @Before
    public void setUp() {
        handle = null;
    }

    @After
    public void tearDown() {
        handle = null;
    }

    @SuppressWarnings("unchecked")
    private <T extends RAbstractVector> T executeExtract(ElementAccessMode mode, T vector, Object... positions) {
        return (T) executeExtract(mode, false, false, vector, positions);
    }

    private RAbstractVector executeExtract(ElementAccessMode mode, boolean exact, boolean dropDimension, Object vector, Object... positions) {
        if (handle == null || this.currentMode != mode || currentExact != exact || currentDropDimension != dropDimension) {
            handle = create(mode, exact, dropDimension);
            this.currentMode = mode;
        }
        if (box == null) {
            box = TestUtilities.createHandle(BoxPrimitiveNodeGen.create(), (node, args) -> node.execute(args[0]));
        }

        return (RAbstractVector) box.call(handle.call(vector, positions));
    }

    private static NodeHandle<ExtractVectorNode> create(ElementAccessMode mode, boolean exact, boolean dropDimension) {
        return TestUtilities.createHandle(ExtractVectorNode.create(mode, false),
                        (node, args) -> node.apply(args[0], (Object[]) args[1], RDataFactory.createLogicalVectorFromScalar(exact), RDataFactory.createLogicalVectorFromScalar(dropDimension)));
    }
}
