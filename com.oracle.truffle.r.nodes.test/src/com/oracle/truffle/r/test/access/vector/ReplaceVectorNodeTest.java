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
import static org.hamcrest.CoreMatchers.sameInstance;
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
import com.oracle.truffle.r.nodes.access.vector.ReplaceVectorNode;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNode;
import com.oracle.truffle.r.nodes.binary.BoxPrimitiveNodeGen;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.test.test.TestBase;
import com.oracle.truffle.r.test.test.TestUtilities;
import com.oracle.truffle.r.test.test.TestUtilities.NodeHandle;

@RunWith(Theories.class)
public class ReplaceVectorNodeTest extends TestBase {
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
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            RDataFactory.createIntVector(new int[]{2, 3, 4}, true), RDataFactory.createIntVector(new int[]{2, 3}, true));
            assertIndicies(vector, 0, 1, 2, 3, 4, 5, -1, -1, -1, 9, 10, -1, -1, -1, 14, 15, 16, 17, 18, 19);

            // replace box with box indices
            vector = TestUtilities.generateInteger(9, true);
            vector.setDimensions(new int[]{3, 3});
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            RDataFactory.createIntVector(new int[]{2, 3}, true), RDataFactory.createIntVector(new int[]{2, 3}, true));
            assertIndicies(vector, 0, 1, 2, 3, -1, -1, 6, -1, -1);

            // replace three dimensions
            vector = TestUtilities.generateInteger(24, true);
            vector.setDimensions(new int[]{2, 3, 4});
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2}, true));
            assertIndicies(vector, 0, 1, 2, 3, 4, 5, 6, 7, 8, -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);

            // replace three dimensions
            vector = TestUtilities.generateInteger(24, true);
            vector.setDimensions(new int[]{2, 3, 4});
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            RDataFactory.createIntVector(new int[]{2}, true), RDataFactory.createIntVector(new int[]{2, 3}, true), RDataFactory.createIntVector(new int[]{2, 3, 4}, true));
            assertIndicies(vector, 0, 1, 2, 3, 4, 5, 6, 7, 8, -1, 10, -1, 12, 13, 14, -1, 16, -1, 18, 19, 20, -1, 22, -1);
            return null;
        });
    }

    private static void assertIndicies(RIntVector vector, int... expectedValues) {
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

            // replace scalar with sequence stride=1
            vector = TestUtilities.generateInteger(9, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1), new Object[]{RDataFactory.createIntSequence(5, 1, 3)});
            assertIndicies(vector, 0, 1, 2, 3, -1, -1, -1, 7, 8);

            // replace scalar with sequence stride>1
            vector = TestUtilities.generateInteger(9, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1), new Object[]{RDataFactory.createIntSequence(5, 2, 2)});
            assertIndicies(vector, 0, 1, 2, 3, -1, 5, -1, 7, 8);

            // replace scalar with negative integer vector
            vector = TestUtilities.generateInteger(4, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1), new Object[]{RDataFactory.createIntVector(new int[]{-2}, true)});
            assertIndicies(vector, -1, 1, -1, -1);

            // replace scalar with logical scalar
            vector = TestUtilities.generateInteger(3, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE}, true)});
            assertIndicies(vector, -1, -1, -1);

            // replace scalar with logical vector
            vector = TestUtilities.generateInteger(4, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1),
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_FALSE}, true)});
            assertIndicies(vector, -1, 1, -1, 3);

            // replace vector indexed by logical vector
            vector = TestUtilities.generateInteger(4, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVector(new int[]{-1, -2}, true),
                            new Object[]{RDataFactory.createLogicalVector(new byte[]{RRuntime.LOGICAL_TRUE, RRuntime.LOGICAL_FALSE}, true)});
            assertIndicies(vector, -1, 1, -2, 3);

            // replace scalar with integer vector
            vector = TestUtilities.generateInteger(9, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1), new Object[]{RDataFactory.createIntVector(new int[]{9, 8}, true)});
            assertIndicies(vector, 0, 1, 2, 3, 4, 5, 6, -1, -1);

            // replace scalar with integer scalar
            vector = TestUtilities.generateInteger(9, true);
            executeReplace(ElementAccessMode.SUBSET, vector, RDataFactory.createIntVectorFromScalar(-1), new Object[]{RDataFactory.createIntVector(new int[]{9}, true)});
            assertIndicies(vector, 0, 1, 2, 3, 4, 5, 6, 7, -1);
            return null;
        });
    }

    @Theory
    public void testNames(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);
            RStringVector names = (RStringVector) TestUtilities.generateVector(RType.Character, 4, true);
            vector.setNames(names);

            RAbstractVector value = TestUtilities.generateVector(targetType, 4, true);
            RStringVector valueNames = (RStringVector) TestUtilities.generateVector(RType.Character, 4, true);
            value.setNames(valueNames);

            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, value, RRuntime.LOGICAL_TRUE);

            RStringVector newNames = result.getNames();
            assertThat(newNames.getLength(), is(names.getLength()));
            assertThat(newNames.getDataAt(0), is(names.getDataAt(0)));
            assertThat(newNames.getDataAt(1), is(names.getDataAt(1)));
            assertThat(newNames.getDataAt(2), is(names.getDataAt(2)));
            assertThat(newNames.getDataAt(3), is(names.getDataAt(3)));
            return null;
        });
    }

    @Theory
    public void testCompletenessAfterReplace(RType targetType) {
        execInContext(() -> {
            assumeTrue(isCacheEnabled());

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, false);
            RAbstractVector replaceWith = TestUtilities.generateVector(targetType, 1, true);

            assumeThat(vector.isComplete(), is(false));
            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, replaceWith, RDataFactory.createIntVectorFromScalar(1));
            assertThat(result.isComplete(), is(false));
            return null;
        });
    }

    @Theory
    public void testCompletenessAfterReplaceAll(RType targetType) {
        execInContext(() -> {
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, false);
            RAbstractVector replaceWith = TestUtilities.generateVector(targetType, 1, true);

            assumeThat(vector.isComplete(), is(false));
            executeReplace(ElementAccessMode.SUBSET, vector, replaceWith, RDataFactory.createLogicalVectorFromScalar(true));

            // TODO we would need to find out if we replace all elements. we should support this.
            // assertThat(result.isComplete(), is(true));
            return null;
        });
    }

    @Theory
    public void testCompletenessPositionNA(RType targetType) {
        execInContext(() -> {
            // It does not make sense to check for NAs in lists, and raw vectors.
            assumeTrue(isCacheEnabled());
            assumeTrue(targetType != RType.Raw);
            assumeTrue(targetType != RType.List);

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);
            RAbstractVector replaceWith = TestUtilities.generateVector(targetType, 1, true);

            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, replaceWith, RRuntime.LOGICAL_NA);

            assertThat(result.isComplete(), is(true));
            return null;
        });
    }

    @Theory
    public void testCompletenessOutOfBounds(RType targetType) {
        execInContext(() -> {
            assumeTrue(targetType != RType.Raw);
            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);
            RAbstractVector replaceWith = TestUtilities.generateVector(targetType, 1, true);

            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, replaceWith, RDataFactory.createIntVectorFromScalar(10));

            assertThat(result.isComplete(), is(false));
            return null;
        });
    }

    @Theory
    public void testCasts(RType targetType, RType valueType) {
        execInContext(() -> {
            if (targetType != valueType) {
                assumeTrue(targetType != RType.Raw && valueType != RType.Raw);
            }
            RType resultType = RType.maxPrecedence(targetType, valueType);

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);
            RAbstractVector value = TestUtilities.generateVector(valueType, 4, true);

            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, value, accessFirst);
            assertThat(result.getRType(), is(resultType));
            return null;
        });
    }

    @Theory
    public void testSubsetSingleDimensionTheory(RType targetType, RAbstractVector position) {
        execInContext(() -> {
            assumeTrue(position.getLength() <= 4);
            assumeTrue(position.getLength() >= 1);
            assumeTrue(position.isComplete());

            RAbstractVector vector = TestUtilities.generateVector(targetType, 4, true);
            RAbstractVector value = TestUtilities.generateVector(targetType, 4, true);

            RAbstractVector result = executeReplace(ElementAccessMode.SUBSET, vector, value, position);
            assertThat(result, is(sameInstance(vector)));
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
            RAbstractVector value = TestUtilities.generateVector(targetType, 1, true);

            executeReplace(ElementAccessMode.SUBSCRIPT, vector, value, position);
            return null;
        });
    }

    private NodeHandle<ReplaceVectorNode> handle;
    private NodeHandle<BoxPrimitiveNode> box;
    private ElementAccessMode currentMode;

    @Before
    public void setUp() {
        handle = null;
    }

    @After
    public void tearDown() {
        handle = null;
    }

    private RAbstractVector executeReplace(ElementAccessMode mode, Object vector, Object value, Object... positions) {
        if (handle == null || this.currentMode != mode) {
            handle = create(mode);
            this.currentMode = mode;
        }
        if (box == null) {
            box = TestUtilities.createHandle(BoxPrimitiveNodeGen.create(), (node, args) -> node.execute(args[0]));
        }

        return (RAbstractVector) box.call(handle.call(vector, positions, value));
    }

    private static NodeHandle<ReplaceVectorNode> create(ElementAccessMode mode) {
        return TestUtilities.createHandle(ReplaceVectorNode.create(mode, false),
                        (node, args) -> node.apply(args[0], (Object[]) args[1], args[2]));
    }
}
