/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.reflect.CallerSensitive;

import java.lang.invoke.MethodType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

/**
 * A stack walker.
 *
 * <p> The {@link StackWalker#walk walk} method opens a sequential stream
 * of {@link StackFrame StackFrame}s for the current thread and then applies
 * the given function to walk the {@code StackFrame} stream.
 * The stream reports stack frame elements in order, from the top most frame
 * that represents the execution point at which the stack was generated to
 * the bottom most frame.
 * The {@code StackFrame} stream is closed when the {@code walk} method returns.
 * If an attempt is made to reuse the closed stream,
 * {@code IllegalStateException} will be thrown.
 *
 * <p> {@linkplain Option <em>Stack walker options</em>} configure the stack frame
 * information obtained by a {@code StackWalker}.
 * By default, the class name and method information are collected but
 * not the {@link StackFrame#getDeclaringClass() Class reference}.
 * The method information can be dropped via the {@link Option#DROP_METHOD_INFO
 * DROP_METHOD_INFO} option. The {@code Class} object can be retained for
 * access via the {@link Option#RETAIN_CLASS_REFERENCE RETAIN_CLASS_REFERENCE} option.
 * Stack frames of the reflection API and implementation classes are
 * {@linkplain Option#SHOW_HIDDEN_FRAMES hidden} by default.
 *
 * <p> {@code StackWalker} is thread-safe. Multiple threads can share
 * a single {@code StackWalker} object to traverse its own stack.
 *
 * @apiNote
 * Examples
 *
 * <p>1. To find the first caller filtering out a known list of implementation class:
 * {@snippet lang="java" :
 *     StackWalker walker = StackWalker.getInstance(Set.of(Option.DROP_METHOD_INFO, Option.RETAIN_CLASS_REFERENCE));
 *     Optional<Class<?>> callerClass = walker.walk(s ->
 *             s.map(StackFrame::getDeclaringClass)
 *              .filter(Predicate.not(implClasses::contains))
 *              .findFirst());
 * }
 *
 * <p>2. To snapshot the top 10 stack frames of the current thread,
 * {@snippet lang="java" :
 *     List<StackFrame> stack = StackWalker.getInstance().walk(s -> s.limit(10).toList());
 * }
 *
 * Unless otherwise noted, passing a {@code null} argument to a
 * constructor or method in this {@code StackWalker} class
 * will cause a {@link NullPointerException NullPointerException}
 * to be thrown.
 *
 * @since 9
 */
@NullMarked
public final class StackWalker {
    /**
     * A {@code StackFrame} object represents a method invocation returned by
     * {@link StackWalker}.
     *
     * <p> {@linkplain Option <em>Stack walker options</em>} configure the stack
     * frame information obtained by a {@code StackWalker}.
     * If the stack walker is configured with {@link Option#DROP_METHOD_INFO
     * DROP_METHOD_INFO} option, method information such as
     * the {@linkplain StackFrame#getMethodName() method name},
     * the {@linkplain StackFrame#getLineNumber() line number},
     * the {@linkplain StackFrame#getByteCodeIndex() bytecode index}, etc
     * will be dropped.
     * If the stack walker is configured with {@link Option#RETAIN_CLASS_REFERENCE
     * RETAIN_CLASS_REFERENCE} option, the {@link #getDeclaringClass() Class} object
     * will be retained for access.
     *
     * @since 9
     * @jvms 2.6 Frames
     */
    public interface StackFrame {
        /**
         * {@return the {@linkplain ClassLoader##binary-name binary name}
         * of the declaring class of the method represented by this stack frame}
         *
         * @jls 13.1 The Form of a Binary
         */
        public String getClassName();

        /**
         * {@return the name of the method represented by this stack frame}
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         */
        public String getMethodName();

        /**
         * {@return the declaring {@code Class} for the method represented by
         * this stack frame}
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         without {@link Option#RETAIN_CLASS_REFERENCE RETAIN_CLASS_REFERENCE} option
         */
        public Class<?> getDeclaringClass();

        /**
         * Returns the {@link MethodType} representing the parameter types and
         * the return type for the method represented by this stack frame.
         *
         * @implSpec
         * The default implementation throws {@code UnsupportedOperationException}.
         *
         * @return the {@code MethodType} of the method represented by this stack frame
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option or
         *         without {@link Option#RETAIN_CLASS_REFERENCE RETAIN_CLASS_REFERENCE} option
         *
         * @since 10
         */
        public default MethodType getMethodType() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns the <i>descriptor</i> of the method represented by
         * this stack frame as defined by
         * <cite>The Java Virtual Machine Specification</cite>.
         *
         * @implSpec
         * The default implementation throws {@code UnsupportedOperationException}.
         *
         * @return the descriptor of the method represented by
         *         this stack frame
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         *
         * @see MethodType#fromMethodDescriptorString(String, ClassLoader)
         * @see MethodType#toMethodDescriptorString()
         * @jvms 4.3.3 Method Descriptors
         *
         * @since 10
         */
        public default String getDescriptor() {
            throw new UnsupportedOperationException();
        }


        /**
         * Returns the index to the code array of the {@code Code} attribute
         * containing the execution point represented by this stack frame.
         * The code array gives the actual bytes of Java Virtual Machine code
         * that implement the method.
         *
         * @return the index to the code array of the {@code Code} attribute
         *         containing the execution point represented by this stack frame,
         *         or a negative number if the method is native.
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         *
         * @jvms 4.7.3 The {@code Code} Attribute
         */
        public int getByteCodeIndex();

        /**
         * Returns the name of the source file containing the execution point
         * represented by this stack frame.  Generally, this corresponds
         * to the {@code SourceFile} attribute of the relevant {@code class}
         * file as defined by <cite>The Java Virtual Machine Specification</cite>.
         * In some systems, the name may refer to some source code unit
         * other than a file, such as an entry in a source repository.
         *
         * @return the name of the file containing the execution point
         *         represented by this stack frame, or {@code null} if
         *         this information is unavailable.
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         *
         * @jvms 4.7.10 The {@code SourceFile} Attribute
         */
        public @Nullable String getFileName();

        /**
         * Returns the line number of the source line containing the execution
         * point represented by this stack frame.  Generally, this is
         * derived from the {@code LineNumberTable} attribute of the relevant
         * {@code class} file as defined by <cite>The Java Virtual Machine
         * Specification</cite>.
         *
         * @return the line number of the source line containing the execution
         *         point represented by this stack frame, or a negative number if
         *         this information is unavailable.
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         *
         * @jvms 4.7.12 The {@code LineNumberTable} Attribute
         */
        public int getLineNumber();

        /**
         * {@return {@code true} if the method containing the execution point
         * represented by this stack frame is a native method}
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         */
        public boolean isNativeMethod();

        /**
         * {@return {@code StackTraceElement} for this stack frame}
         *
         * @throws UnsupportedOperationException if the {@code StackWalker} is configured
         *         with {@link Option#DROP_METHOD_INFO DROP_METHOD_INFO} option
         */
        public StackTraceElement toStackTraceElement();
    }

    /**
     * Stack walker option to configure the {@linkplain StackFrame stack frame}
     * information obtained by a {@code StackWalker}.
     *
     * @since 9
     */
    public enum Option {
        /**
         * Retains {@code Class} object in {@code StackFrame}s
         * walked by this {@code StackWalker}.
         *
         * <p> A {@code StackWalker} configured with this option will support
         * {@link StackWalker#getCallerClass()} and
         * {@link StackFrame#getDeclaringClass() StackFrame.getDeclaringClass()}.
         */
        RETAIN_CLASS_REFERENCE,
        /**
         * Drops the method information from {@code StackFrame}s
         * walked by this {@code StackWalker}.
         *
         * <p> A {@code StackWalker} configured with this option will drop
         * the {@linkplain StackFrame#getMethodName() method name},
         * the {@linkplain StackFrame#getMethodType() method type},
         * the {@linkplain StackFrame#getLineNumber() line number},
         * the {@linkplain StackFrame#getByteCodeIndex() bytecode index},
         * the {@linkplain StackFrame#getFileName() source file name} and
         * {@linkplain StackFrame#isNativeMethod() native method or not}.
         *
         * @since 22
         */
        DROP_METHOD_INFO,
        /**
         * Shows all reflection frames.
         *
         * <p>By default, reflection frames are hidden.  A {@code StackWalker}
         * configured with this {@code SHOW_REFLECT_FRAMES} option
         * will show all reflection frames that
         * include {@link java.lang.reflect.Method#invoke} and
         * {@link java.lang.reflect.Constructor#newInstance(Object...)}
         * and their reflection implementation classes.
         *
         * <p>The {@link #SHOW_HIDDEN_FRAMES} option can also be used to show all
         * reflection frames and it will also show other hidden frames that
         * are implementation-specific.
         *
         * @apiNote
         * This option includes the stack frames representing the invocation of
         * {@code Method} and {@code Constructor}.  Any utility methods that
         * are equivalent to calling {@code Method.invoke} or
         * {@code Constructor.newInstance} such as {@code Class.newInstance}
         * are not filtered or controlled by any stack walking option.
         */
        SHOW_REFLECT_FRAMES,
        /**
         * Shows all hidden frames.
         *
         * <p>A Java Virtual Machine implementation may hide implementation
         * specific frames in addition to {@linkplain #SHOW_REFLECT_FRAMES
         * reflection frames}. A {@code StackWalker} with this {@code SHOW_HIDDEN_FRAMES}
         * option will show all hidden frames (including reflection frames).
         */
        SHOW_HIDDEN_FRAMES;
    }

    enum ExtendedOption {
        /**
         * Obtain monitors, locals and operands.
         */
        LOCALS_AND_OPERANDS
    };

    static final EnumSet<Option> DEFAULT_EMPTY_OPTION = EnumSet.noneOf(Option.class);

    private static final StackWalker DEFAULT_WALKER =
        new StackWalker(DEFAULT_EMPTY_OPTION);

    private final Continuation continuation;
    private final ContinuationScope contScope;
    private final Set<Option> options;
    private final ExtendedOption extendedOption;
    private final int estimateDepth;
    final boolean retainClassRef; // cached for performance

    /**
     * Returns a {@code StackWalker} instance.
     *
     * <p> This {@code StackWalker} is configured to skip all
     * {@linkplain Option#SHOW_HIDDEN_FRAMES hidden frames} and
     * no {@linkplain Option#RETAIN_CLASS_REFERENCE class reference} is retained.
     *
     * @return a {@code StackWalker} configured to skip all
     * {@linkplain Option#SHOW_HIDDEN_FRAMES hidden frames} and
     * no {@linkplain Option#RETAIN_CLASS_REFERENCE class reference} is retained.
     *
     */
    public static StackWalker getInstance() {
        // no permission check needed
        return DEFAULT_WALKER;
    }

    /**
     * Returns a {@code StackWalker} instance with the given option specifying
     * the stack frame information it can access.
     *
     * @param option {@link Option stack walking option}
     *
     * @return a {@code StackWalker} configured with the given option
     */
    public static StackWalker getInstance(Option option) {
        return getInstance(EnumSet.of(Objects.requireNonNull(option)));
    }

    /**
     * Returns a {@code StackWalker} instance with the given {@code options} specifying
     * the stack frame information it can access.
     *
     * <p>
     * If the given {@code options} is empty, this {@code StackWalker} is
     * configured to skip all {@linkplain Option#SHOW_HIDDEN_FRAMES hidden frames}
     * and no {@linkplain Option#RETAIN_CLASS_REFERENCE class reference} is retained.
     *
     * @param options {@link Option stack walking options}
     *
     * @return a {@code StackWalker} configured with the given options
     */
    public static StackWalker getInstance(Set<Option> options) {
        if (options.isEmpty()) {
            return DEFAULT_WALKER;
        }

        EnumSet<Option> optionSet = toEnumSet(options);
        return new StackWalker(optionSet);
    }

    /**
     * Returns a {@code StackWalker} instance with the given {@code options} specifying
     * the stack frame information it can access.
     *
     * <p>
     * If the given {@code options} is empty, this {@code StackWalker} is
     * configured to skip all {@linkplain Option#SHOW_HIDDEN_FRAMES hidden frames}
     * and no {@linkplain Option#RETAIN_CLASS_REFERENCE class reference} is retained.
     *
     * <p>
     * The {@code estimateDepth} specifies the estimate number of stack frames
     * this {@code StackWalker} will traverse that the {@code StackWalker} could
     * use as a hint for the buffer size.
     *
     * @param options {@link Option stack walking options}
     * @param estimateDepth Estimate number of stack frames to be traversed.
     *
     * @return a {@code StackWalker} configured with the given options
     *
     * @throws IllegalArgumentException if {@code estimateDepth <= 0}
     */
    public static StackWalker getInstance(Set<Option> options, int estimateDepth) {
        if (estimateDepth <= 0) {
            throw new IllegalArgumentException("estimateDepth must be > 0");
        }
        EnumSet<Option> optionSet = toEnumSet(options);
        return new StackWalker(optionSet, estimateDepth);
    }

    // ----- private constructors ------
    private StackWalker(EnumSet<Option> options) {
        this(options, 0, null, null, null);
    }
    private StackWalker(EnumSet<Option> options, int estimateDepth) {
        this(options, estimateDepth, null, null, null);
    }
    private StackWalker(EnumSet<Option> options,
                        int estimateDepth,
                        ExtendedOption extendedOption,
                        ContinuationScope contScope,
                        Continuation continuation) {
        this.options = options;
        this.estimateDepth = estimateDepth;
        this.extendedOption = extendedOption;
        this.retainClassRef = hasOption(Option.RETAIN_CLASS_REFERENCE);
        this.contScope = contScope;
        this.continuation = continuation;
    }

    /*
     * Returns a defensive copy
     */
    private static EnumSet<Option> toEnumSet(Set<Option> options) {
        Objects.requireNonNull(options);
        if (options.isEmpty()) {
            return DEFAULT_EMPTY_OPTION;
        } else {
            return EnumSet.copyOf(options);
        }
    }

    /**
     * Applies the given function to the stream of {@code StackFrame}s
     * for the current thread, traversing from the top frame of the stack,
     * which is the method calling this {@code walk} method.
     *
     * <p>The {@code StackFrame} stream will be closed when
     * this method returns.  When a closed {@code Stream<StackFrame>} object
     * is reused, {@code IllegalStateException} will be thrown.
     *
     * @apiNote
     * For example, to find the first 10 calling frames, first skipping those frames
     * whose declaring class is in package {@code com.foo}:
     * {@snippet lang="java" :
     * List<StackFrame> frames = StackWalker.getInstance().walk(s ->
     *         s.dropWhile(f -> f.getClassName().startsWith("com.foo."))
     *          .limit(10)
     *          .toList());
     * }
     *
     * <p>This method takes a {@code Function} accepting a {@code Stream<StackFrame>},
     * rather than returning a {@code Stream<StackFrame>} and allowing the
     * caller to directly manipulate the stream. The Java virtual machine is
     * free to reorganize a thread's control stack, for example, via
     * deoptimization. By taking a {@code Function} parameter, this method
     * allows access to stack frames through a stable view of a thread's control
     * stack.
     *
     * <p>Parallel execution is effectively disabled and stream pipeline
     * execution will only occur on the current thread.
     *
     * @implNote The implementation stabilizes the stack by anchoring a frame
     * specific to the stack walking and ensures that the stack walking is
     * performed above the anchored frame. When the stream object is closed or
     * being reused, {@code IllegalStateException} will be thrown.
     *
     * @param function a function that takes a stream of
     *                 {@linkplain StackFrame stack frames} and returns a result.
     * @param <T> The type of the result of applying the function to the
     *            stream of {@linkplain StackFrame stack frame}.
     *
     * @return the result of applying the function to the stream of
     *         {@linkplain StackFrame stack frame}.
     */
    @CallerSensitive
    public <T extends @Nullable Object> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        // Returning a Stream<StackFrame> would be unsafe, as the stream could
        // be used to access the stack frames in an uncontrolled manner.  For
        // example, a caller might pass a Spliterator of stack frames after one
        // or more frames had been traversed. There is no robust way to detect
        // whether the execution point when
        // Spliterator.tryAdvance(java.util.function.Consumer<? super T>) is
        // invoked is the exact same execution point where the stack frame
        // traversal is expected to resume.

        Objects.requireNonNull(function);
        return StackStreamFactory.makeStackTraverser(this, function)
                                 .walk();
    }

    /**
     * Performs the given action on each element of {@code StackFrame} stream
     * of the current thread, traversing from the top frame of the stack,
     * which is the method calling this {@code forEach} method.
     *
     * <p> This method is equivalent to calling
     * <blockquote>
     * {@code walk(s -> { s.forEach(action); return null; });}
     * </blockquote>
     *
     * @param action an action to be performed on each {@code StackFrame}
     *               of the stack of the current thread
     */
    @CallerSensitive
    public void forEach(Consumer<? super StackFrame> action) {
        Objects.requireNonNull(action);
        StackStreamFactory.makeStackTraverser(this, s -> {
            s.forEach(action);
            return null;
        }).walk();
    }

    /**
     * Gets the {@code Class} object of the caller who invoked the method
     * that invoked {@code getCallerClass}.
     *
     * <p> This method filters {@linkplain Option#SHOW_REFLECT_FRAMES reflection
     * frames}, {@link java.lang.invoke.MethodHandle}, and
     * {@linkplain Option#SHOW_HIDDEN_FRAMES hidden frames} regardless of the
     * {@link Option#SHOW_REFLECT_FRAMES SHOW_REFLECT_FRAMES}
     * and {@link Option#SHOW_HIDDEN_FRAMES SHOW_HIDDEN_FRAMES} options
     * this {@code StackWalker} has been configured with.
     *
     * <p> This method should be called when a caller frame is present.  If
     * it is called from the bottom most frame on the stack,
     * {@code IllegalCallerException} will be thrown.
     *
     * <p> This method throws {@code UnsupportedOperationException}
     * if this {@code StackWalker} is not configured with the
     * {@link Option#RETAIN_CLASS_REFERENCE RETAIN_CLASS_REFERENCE} option.
     *
     * @apiNote
     * For example, {@code Util::getResourceBundle} loads a resource bundle
     * on behalf of the caller.  It invokes {@code getCallerClass} to identify
     * the class whose method called {@code Util::getResourceBundle}.
     * Then, it obtains the class loader of that class, and uses
     * the class loader to load the resource bundle. The caller class
     * in this example is {@code MyTool}.
     *
     * {@snippet lang="java" :
     * class Util {
     *     private final StackWalker walker =
     *         StackWalker.getInstance(Set.of(Option.DROP_METHOD_INFO, Option.RETAIN_CLASS_REFERENCE));
     *     public ResourceBundle getResourceBundle(String bundleName) {
     *         Class<?> caller = walker.getCallerClass();
     *         return ResourceBundle.getBundle(bundleName, Locale.getDefault(), caller.getClassLoader());
     *     }
     * }
     *
     * class MyTool {
     *     private final Util util = new Util();
     *     private void init() {
     *         ResourceBundle rb = util.getResourceBundle("mybundle");
     *     }
     * }
     * }
     *
     * An equivalent way to find the caller class using the
     * {@link StackWalker#walk walk} method is as follows
     * (filtering the reflection frames, {@code MethodHandle} and hidden frames
     * not shown below):
     * {@snippet lang="java" :
     *     Optional<Class<?>> caller = walker.walk(s ->
     *         s.map(StackFrame::getDeclaringClass)
     *          .skip(2)
     *          .findFirst());
     * }
     *
     * When the {@code getCallerClass} method is called from a method that
     * is the bottom most frame on the stack,
     * for example, {@code static public void main} method launched by the
     * {@code java} launcher, or a method invoked from a JNI attached thread,
     * {@code IllegalCallerException} is thrown.
     *
     * @return {@code Class} object of the caller's caller invoking this method.
     *
     * @throws UnsupportedOperationException if this {@code StackWalker}
     *         is not configured with {@link Option#RETAIN_CLASS_REFERENCE
     *         Option.RETAIN_CLASS_REFERENCE}.
     * @throws IllegalCallerException if there is no caller frame, i.e.
     *         when this {@code getCallerClass} method is called from a method
     *         which is the last frame on the stack.
     */
    @CallerSensitive
    public Class<?> getCallerClass() {
        if (!retainClassRef) {
            throw new UnsupportedOperationException("This stack walker " +
                    "does not have RETAIN_CLASS_REFERENCE access");
        }
        if (continuation != null) {
            throw new UnsupportedOperationException("This stack walker walks a continuation");
        }

        return StackStreamFactory.makeCallerFinder(this).findCaller();
    }

    // ---- package access ----

    static StackWalker newInstance(Set<Option> options, ExtendedOption extendedOption) {
        return newInstance(options, extendedOption, null);
    }

    static StackWalker newInstance(Set<Option> options, ContinuationScope contScope) {
        return newInstance(options, null, contScope);
    }

    static StackWalker newInstance(Set<Option> options, ExtendedOption extendedOption, ContinuationScope contScope) {
        return newInstance(options, extendedOption, contScope, null);
    }

    static StackWalker newInstance(Set<Option> options, ExtendedOption extendedOption, ContinuationScope contScope, Continuation continuation) {
        EnumSet<Option> optionSet = toEnumSet(options);
        return new StackWalker(optionSet, 0, extendedOption, contScope, continuation);
    }

    int estimateDepth() {
        return estimateDepth;
    }

    boolean hasOption(Option option) {
        return options.contains(option);
    }

    boolean hasLocalsOperandsOption() {
        return extendedOption == ExtendedOption.LOCALS_AND_OPERANDS;
    }

    ContinuationScope getContScope() {
        return contScope;
    }

    Continuation getContinuation() {
        return continuation;
    }
}
