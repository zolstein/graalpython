/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.runtime.PythonOptions.CatchAllExceptions;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode.ExceptBlockMR.CatchesFunction;
import com.oracle.graal.python.nodes.statement.TryExceptNode.ExceptBlockMR.CatchesFunction.ExceptListMR.ExecuteNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public class TryExceptNode extends StatementNode implements TruffleObject {

    @Child private PNode body;
    @Children private final ExceptNode[] exceptNodes;
    @Child private PNode orelse;

    @CompilationFinal CatchesFunction catchesFunction;

    @CompilationFinal boolean seenException;

    public TryExceptNode(PNode body, ExceptNode[] exceptNodes, PNode orelse) {
        this.body = body;
        body.markAsTryBlock();
        this.exceptNodes = exceptNodes;
        this.orelse = orelse;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            body.execute(frame);
        } catch (PException ex) {
            catchException(frame, ex);
            return PNone.NONE;
        } catch (Exception e) {
            if (!seenException) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenException = true;
            }

            if (PythonOptions.getOption(getContext(), CatchAllExceptions)) {
                if (e instanceof ControlFlowException) {
                    throw e;
                } else {
                    PException pe = new PException(getBaseException(e), this);
                    try {
                        catchException(frame, pe);
                    } catch (PException pe_thrown) {
                        if (pe_thrown != pe) {
                            throw e;
                        }
                    }
                }
            } else {
                throw e;
            }
        }
        return orelse.execute(frame);
    }

    @TruffleBoundary
    private PBaseException getBaseException(Throwable t) {
        return factory().createBaseException(getCore().getErrorClass(PythonErrorType.ValueError), t.getMessage(), new Object[0]);
    }

    @ExplodeLoop
    private void catchException(VirtualFrame frame, PException exception) {
        boolean wasHandled = false;
        for (ExceptNode exceptNode : exceptNodes) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (!wasHandled) {
                if (exceptNode.matchesException(frame, exception)) {
                    try {
                        exceptNode.executeExcept(frame, exception);
                    } catch (ExceptionHandledException e) {
                        wasHandled = true;
                    }
                }
            }
        }
        if (!wasHandled) {
            throw exception;
        }
    }

    public PNode getBody() {
        return body;
    }

    public ExceptNode[] getExceptNodes() {
        return exceptNodes;
    }

    public PNode getOrelse() {
        return orelse;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return ExceptBlockMRForeign.ACCESS;
    }

    @MessageResolution(receiverType = TryExceptNode.class)
    static class ExceptBlockMR {
        @Resolve(message = "HAS_KEYS")
        abstract static class HasKeysNode extends Node {
            Object access(@SuppressWarnings("unused") TryExceptNode object) {
                return true;
            }
        }

        @Resolve(message = "KEYS")
        abstract static class KeysNode extends Node {
            Object access(TryExceptNode object) {
                return object.getContext().getEnv().asGuestValue(new String[]{StandardTags.TryBlockTag.CATCHES});
            }
        }

        @Resolve(message = "KEY_INFO")
        abstract static class KeyInfoNode extends Node {
            Object access(@SuppressWarnings("unused") TryExceptNode object, String name) {
                if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                    return KeyInfo.INVOCABLE | KeyInfo.READABLE;
                } else {
                    return KeyInfo.NONE;
                }
            }
        }

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {
            @Child GetAttributeNode getAttr = GetAttributeNode.create();

            CatchesFunction access(TryExceptNode object, String name) {
                return doit(object, name, getAttr);
            }

            static CatchesFunction doit(TryExceptNode object, String name, GetAttributeNode getAttr) {
                if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                    if (object.catchesFunction == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        ArrayList<Object> literalCatches = new ArrayList<>();
                        ExceptNode[] exceptNodes = object.getExceptNodes();
                        PythonModule builtins = object.getContext().getBuiltins();

                        for (ExceptNode node : exceptNodes) {
                            PNode exceptType = node.getExceptType();
                            if (exceptType instanceof ReadGlobalOrBuiltinNode) {
                                try {
                                    literalCatches.add(getAttr.execute(builtins, ((ReadGlobalOrBuiltinNode) exceptType).getAttributeId()));
                                } catch (PException e) {
                                }
                            } else if (exceptType instanceof TupleLiteralNode) {
                                for (PNode tupleValue : ((TupleLiteralNode) exceptType).getValues()) {
                                    if (tupleValue instanceof ReadGlobalOrBuiltinNode) {
                                        try {
                                            literalCatches.add(getAttr.execute(builtins, ((ReadGlobalOrBuiltinNode) tupleValue).getAttributeId()));
                                        } catch (PException e) {
                                        }
                                    }
                                }
                            }
                        }

                        Object isinstanceFunc = getAttr.execute(builtins, BuiltinNames.ISINSTANCE);
                        PTuple caughtClasses = object.factory().createTuple(literalCatches.toArray());

                        if (isinstanceFunc instanceof PBuiltinFunction) {
                            RootCallTarget callTarget = ((PBuiltinFunction) isinstanceFunc).getCallTarget();
                            object.catchesFunction = new CatchesFunction(callTarget, caughtClasses);
                        } else {
                            throw new IllegalStateException("isinstance was redefined, cannot check exceptions");
                        }
                    }
                    return object.catchesFunction;
                } else {
                    throw UnknownIdentifierException.raise(name);
                }
            }
        }

        @Resolve(message = "INVOKE")
        abstract static class InvokeNode extends Node {
            @Child GetAttributeNode getAttr = GetAttributeNode.create();

            Object access(TryExceptNode object, String name, Object[] arguments) {
                CatchesFunction catchesFunction = ReadNode.doit(object, name, getAttr);
                return ExecuteNode.access(catchesFunction, arguments);
            }
        }

        @CanResolve
        abstract static class CheckFunction extends Node {
            protected static boolean test(TruffleObject receiver) {
                return receiver instanceof TryExceptNode;
            }
        }

        protected static class CatchesFunction implements TruffleObject {
            private final RootCallTarget isInstance;
            private final Object[] args = PArguments.create(2);

            CatchesFunction(RootCallTarget callTarget, PTuple caughtClasses) {
                this.isInstance = callTarget;
                PArguments.setArgument(args, 1, caughtClasses);
            }

            @ExplodeLoop
            boolean catches(Object exception) {
                if (exception instanceof PBaseException) {
                    PArguments.setArgument(args, 0, exception);
                    try {
                        return isInstance.call(args) == Boolean.TRUE;
                    } catch (PException e) {
                    }
                }
                return false;
            }

            public ForeignAccess getForeignAccess() {
                return ExceptListMRForeign.ACCESS;
            }

            @MessageResolution(receiverType = CatchesFunction.class)
            static class ExceptListMR {
                @Resolve(message = "IS_EXECUTABLE")
                abstract static class IsExecutableNode extends Node {
                    Object access(@SuppressWarnings("unused") CatchesFunction object) {
                        return true;
                    }
                }

                @Resolve(message = "EXECUTE")
                abstract static class ExecuteNode extends Node {
                    static Object access(CatchesFunction object, Object[] arguments) {
                        if (arguments.length != 1) {
                            throw ArityException.raise(1, arguments.length);
                        }
                        return object.catches(arguments[0]);
                    }
                }

                @CanResolve
                abstract static class CheckFunction extends Node {
                    protected static boolean test(TruffleObject receiver) {
                        return receiver instanceof TryExceptNode;
                    }
                }
            }
        }
    }
}
