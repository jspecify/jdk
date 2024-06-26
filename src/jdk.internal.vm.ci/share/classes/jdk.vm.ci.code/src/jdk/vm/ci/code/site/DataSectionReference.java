/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.vm.ci.code.site;

import org.jspecify.annotations.Nullable;

/**
 * Represents a pointer to some location in the data section that should be {@link DataPatch
 * patched} into the code.
 */
public final class DataSectionReference extends Reference {

    private boolean initialized;
    private int offset;

    public DataSectionReference() {
        // will be set after the data section layout is fixed
        offset = 0xDEADDEAD;
    }

    public int getOffset() {
        assert initialized;

        return offset;
    }

    public void setOffset(int offset) {
        assert !initialized;
        initialized = true;

        this.offset = offset;
    }

    @Override
    public int hashCode() {
        return offset;
    }

    @Override
    
    
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DataSectionReference) {
            DataSectionReference that = (DataSectionReference) obj;
            return this.offset == that.offset;
        }
        return false;
    }

    @Override
    public String toString() {
        if (initialized) {
            return String.format("DataSection[0x%x]", offset);
        } else {
            return "DataSection[?]";
        }
    }
}
