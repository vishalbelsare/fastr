/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.NativeDataAccess.NativeMirror;

/**
 * <p>
 * This class enables that foreign objects be passed from R to the native code and back.
 * </p>
 * <p>
 * Meant to be used only in {@link com.oracle.truffle.r.runtime.ffi.FFIMaterializeNode} and
 * {@link com.oracle.truffle.r.runtime.ffi.FFIUnwrapNode} together with
 * {@link InteropLibrary#isPointer(Object) }, {@link InteropLibrary#asPointer(Object)} and
 * {@link InteropLibrary#toNative(Object)} implemented in {@link NativeMirror}.
 * </p>
 */
public final class RForeignObjectWrapper extends RBaseObject implements RForeignVectorWrapper {

    protected final TruffleObject delegate;

    public RForeignObjectWrapper(TruffleObject delegate) {
        this.delegate = delegate;
    }

    public TruffleObject getDelegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return RRuntime.NULL;
    }

    @Override
    public RType getRType() {
        return RType.TruffleObject;
    }
}