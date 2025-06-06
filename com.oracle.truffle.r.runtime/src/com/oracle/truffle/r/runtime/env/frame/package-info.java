/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
/**
 * This package contains all the code related to the "frame" access aspect of an
 * {@link com.oracle.truffle.r.runtime.env.REnvironment}. The basic interface assumed by
 * {@link com.oracle.truffle.r.runtime.env.REnvironment} is defined in
 * {@link com.oracle.truffle.r.runtime.env.frame.REnvFrameAccess}, which provides default
 * implementations, most of which fail. The subclass
 * {@link com.oracle.truffle.r.runtime.env.frame.REnvTruffleFrameAccess} handles Truffle frames and
 * locking/unlocking bindings.
 */
@SuppressPackageWarnings({"truffle-inlining", "truffle-sharing", "truffle-neverdefault", "truffle-limit", "truffle-guard", "deprecated"})
package com.oracle.truffle.r.runtime.env.frame;

import com.oracle.truffle.api.dsl.SuppressPackageWarnings;
