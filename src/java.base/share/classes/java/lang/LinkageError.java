/*
 * Copyright (c) 1995, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Subclasses of {@code LinkageError} indicate that a class has
 * some dependency on another class; however, the latter class has
 * incompatibly changed after the compilation of the former class.
 *
 *
 * @author  Frank Yellin
 * @since   1.0
 */
@NullMarked
public class LinkageError extends Error {
    @java.io.Serial
    private static final long serialVersionUID = 3579600108157160122L;

    /**
     * Constructs a {@code LinkageError} with no detail message.
     */
    
    public LinkageError() {
        super();
    }

    /**
     * Constructs a {@code LinkageError} with the specified detail
     * message.
     *
     * @param   s   the detail message.
     */
    
    public LinkageError(@Nullable String s) {
        super(s);
    }

    /**
     * Constructs a {@code LinkageError} with the specified detail
     * message and cause.
     *
     * @param s     the detail message.
     * @param cause the cause, may be {@code null}
     * @since 1.7
     */
    
    public LinkageError(@Nullable String s, @Nullable Throwable cause) {
        super(s, cause);
    }
}
