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

import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.r.nodes.access.vector.SearchFirstStringNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.test.test.TestBase;
import com.oracle.truffle.r.test.test.TestUtilities;
import com.oracle.truffle.r.test.test.TestUtilities.NodeHandle;

@RunWith(Theories.class)
public class StringSearchNodeTest extends TestBase {
    @Test
    public void dummy() {
        // to make sure this file is recognized as a test
    }

    // Please note that "FB" and "Ea" produce a hash collision. Thats why its tested here.
    @DataPoints public static String[] TEST = {RRuntime.STRING_NA, "a", "abc", "bc".intern(), "bc".intern(), "FB", "Ea"};

    @Theory
    public void testTheory(String aString, String bString, String cString) {
        execInContext(() -> {
            create();

            RStringVector a;
            RStringVector b;

            a = createVector(aString);
            b = createVector(bString);
            assertResult(a, b, executeSearch(a, b));

            a = createVector(aString, bString);
            b = createVector(cString);
            assertResult(a, b, executeSearch(a, b));

            a = createVector(aString);
            b = createVector(bString, cString);
            assertResult(a, b, executeSearch(a, b));

            a = createVector(aString, bString);
            b = createVector(bString, cString);
            assertResult(a, b, executeSearch(a, b));
            return null;
        });
    }

    private static RStringVector createVector(String... elements) {
        boolean complete = true;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] == RRuntime.STRING_NA) {
                complete = false;
                break;
            }
        }
        return RDataFactory.createStringVector(elements, complete);
    }

    private static void assertResult(RStringVector a, RStringVector b, RIntVector result) {
        assertThat(result.getLength(), is(b.getLength()));
        for (int i = 0; i < b.getLength(); i++) {
            int resultIndex = result.getDataAt(i);
            String element = b.getDataAt(i);
            if (element == RRuntime.STRING_NA) {
                assertThat(resultIndex > a.getLength(), is(true));
                continue;
            }
            if (resultIndex > a.getLength()) {
                for (int j = 0; j < a.getLength(); j++) {
                    assertCompare(false, a.getDataAt(j), element);
                }
            } else {
                for (int j = 0; j < resultIndex - 1; j++) {
                    assertCompare(false, a.getDataAt(j), element);
                }
                assertCompare(true, a.getDataAt(resultIndex - 1), element);
            }
        }
    }

    private static void assertCompare(boolean expectedResult, String target, String element) {
        boolean actualResult;
        actualResult = target.equals(element);
        assertThat(actualResult, is(expectedResult));
    }

    private NodeHandle<SearchFirstStringNode> handle;

    private void create() {
        handle = TestUtilities.createHandle(SearchFirstStringNode.createNode(true, false),
                        (node, args) -> {
                            RStringVector target = (RStringVector) args[0];
                            return node.apply(target, (RStringVector) args[1], target.getLength(), null);
                        });
    }

    private RIntVector executeSearch(RStringVector a, RStringVector b) {
        return (RIntVector) handle.call(a, b);
    }
}
