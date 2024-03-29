/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a type.  A type can be a class or interface, an
 * invocation (like {@code List<String>}) of a generic class or interface,
 * a type variable, a wildcard type ("{@code ?}"),
 * or a primitive data type (like {@code char}).
 *
 * @since 1.2
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Scott Seligman (generics)
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@NullMarked
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public interface Type {

    /**
     * Return unqualified name of type excluding any dimension information.
     * <p>
     * For example, a two dimensional array of String returns
     * "{@code String}".
     * @return unqualified name of type excluding any dimension information.
     */
    String typeName();

    /**
     * Return qualified name of type excluding any dimension information.
     *<p>
     * For example, a two dimensional array of String
     * returns "{@code java.lang.String}".
     * @return qualified name of this type excluding any dimension information.
     */
    @BinaryNameOrPrimitiveType String qualifiedTypeName();

    /**
     * Return the simple name of this type excluding any dimension information.
     * This is the unqualified name of the type, except that for nested types
     * only the identifier of the innermost type is included.
     * <p>
     * For example, the class {@code Outer.Inner} returns
     * "{@code Inner}".
     *
     * @since 1.5
     * @return the simple name of this type excluding any dimension information.
     */
    @IdentifierOrPrimitiveType String simpleTypeName();

    /**
     * Return the type's dimension information, as a string.
     * <p>
     * For example, a two dimensional array of String returns
     * "{@code [][]}".
     * @return the type's dimension information as a string.
     */
    String dimension();

    /**
     * Return a string representation of the type.
     * This includes any dimension information and type arguments.
     * <p>
     * For example, a two dimensional array of String may return
     * "{@code java.lang.String[][]}",
     * and the parameterized type {@code List<Integer>} may return
     * "{@code java.util.List<java.lang.Integer>}".
     *
     * @return a string representation of the type.
     */
    String toString();

    /**
     * Return true if this type represents a primitive type.
     *
     * @return true if this type represents a primitive type.
     * @since 1.5
     */
    
    boolean isPrimitive();

    /**
     * Return this type as a {@code ClassDoc} if it represents a class
     * or interface.  Array dimensions are ignored.
     * If this type is a {@code ParameterizedType},
     * {@code TypeVariable}, or {@code WildcardType}, return
     * the {@code ClassDoc} of the type's erasure.  If this is an
     * {@code AnnotationTypeDoc}, return this as a {@code ClassDoc}
     * (but see {@link #asAnnotationTypeDoc()}).
     * If this is a primitive type, return null.
     *
     * @return the {@code ClassDoc} of this type,
     *         or null if it is a primitive type.
     */
    ClassDoc asClassDoc();

    /**
     * Return this type as a {@code ParameterizedType} if it represents
     * an invocation of a generic class or interface.  Array dimensions
     * are ignored.
     *
     * @return a {@code ParameterizedType} if the type is an
     *         invocation of a generic type, or null if it is not.
     * @since 1.5
     */
    ParameterizedType asParameterizedType();

    /**
     * Return this type as a {@code TypeVariable} if it represents
     * a type variable.  Array dimensions are ignored.
     *
     * @return a {@code TypeVariable} if the type is a type variable,
     *         or null if it is not.
     * @since 1.5
     */
    TypeVariable asTypeVariable();

    /**
     * Return this type as a {@code WildcardType} if it represents
     * a wildcard type.
     *
     * @return a {@code WildcardType} if the type is a wildcard type,
     *         or null if it is not.
     * @since 1.5
     */
    WildcardType asWildcardType();

    /**
     * Returns this type as a {@code AnnotatedType} if it represents
     * an annotated type.
     *
     * @return a {@code AnnotatedType} if the type if an annotated type,
     *         or null if it is not
     * @since 1.8
     */
    AnnotatedType asAnnotatedType();

    /**
     * Return this type as an {@code AnnotationTypeDoc} if it represents
     * an annotation type.  Array dimensions are ignored.
     *
     * @return an {@code AnnotationTypeDoc} if the type is an annotation
     *         type, or null if it is not.
     * @since 1.5
     */
    AnnotationTypeDoc asAnnotationTypeDoc();

    /**
     * If this type is an array type, return the element type of the
     * array. Otherwise, return null.
     *
     * @return a {@code Type} representing the element type or null.
     * @since 1.8
     */
    Type getElementType();
}
