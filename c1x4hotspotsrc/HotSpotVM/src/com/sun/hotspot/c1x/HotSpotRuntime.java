/*
 * Copyright (c) 2009 Sun Microsystems, Inc. All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product that is
 * described in this document. In particular, and without limitation, these intellectual property rights may include one
 * or more of the U.S. patents listed at http://www.sun.com/patents and one or more additional patents or pending patent
 * applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun Microsystems, Inc. standard
 * license agreement and applicable provisions of the FAR and its supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or registered
 * trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks are used under license and
 * are trademarks or registered trademarks of SPARC International, Inc. in the U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open Company, Ltd.
 */
package com.sun.hotspot.c1x;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.sun.cri.ci.CiConstant;
import com.sun.cri.ci.CiMethodInvokeArguments;
import com.sun.cri.ci.CiTargetMethod;
import com.sun.cri.ci.CiTargetMethod.Call;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.RiConstantPool;
import com.sun.cri.ri.RiField;
import com.sun.cri.ri.RiMethod;
import com.sun.cri.ri.RiOsrFrame;
import com.sun.cri.ri.RiRuntime;
import com.sun.cri.ri.RiSnippets;
import com.sun.cri.ri.RiType;
import com.sun.max.asm.InstructionSet;
import com.sun.max.asm.dis.DisassembledObject;
import com.sun.max.asm.dis.Disassembler;
import com.sun.max.asm.dis.DisassemblyPrinter;

/**
 *
 * @author Thomas Wuerthinger
 *
 *         CRI runtime implementation for the HotSpot VM.
 *
 */
public class HotSpotRuntime implements RiRuntime {

    private final HotSpotVMConfig config;

    public static enum Entrypoints {
        UNVERIFIED, VERIFIED
    }

    public HotSpotRuntime(HotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    public int basicObjectLockOffsetInBytes() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int codeOffset() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String disassemble(byte[] code) {
        return disassemble(code, new DisassemblyPrinter(false));
    }

    private String disassemble(byte[] code, DisassemblyPrinter disassemblyPrinter) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final InstructionSet instructionSet = InstructionSet.AMD64;
        Disassembler.disassemble(byteArrayOutputStream, code, instructionSet, null, 0, null, disassemblyPrinter);
        return byteArrayOutputStream.toString();
    }

    @Override
    public String disassemble(final CiTargetMethod targetMethod) {

        final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {

            private String toString(Call call) {
                if (call.runtimeCall != null) {
                    return "{" + call.runtimeCall.name() + "}";
                } else if (call.symbol != null) {
                    return "{" + call.symbol + "}";
                } else if (call.globalStubID != null) {
                    return "{" + call.globalStubID + "}";
                } else {
                    return "{" + call.method + "}";
                }
            }

            private String siteInfo(int pcOffset) {
                for (Call call : targetMethod.directCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Call call : targetMethod.indirectCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Safepoint site : targetMethod.safepoints) {
                    if (site.pcOffset == pcOffset) {
                        return "{safepoint}";
                    }
                }
                for (DataPatch site : targetMethod.dataReferences) {
                    if (site.pcOffset == pcOffset) {
                        return "{" + site.data + "}";
                    }
                }
                return null;
            }

            @Override
            protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                final String string = super.disassembledObjectString(disassembler, disassembledObject);

                String site = siteInfo(disassembledObject.startPosition());
                if (site != null) {
                    return string + " " + site;
                }
                return string;
            }
        };
        return disassemble(targetMethod.targetCode(), disassemblyPrinter);
    }

    @Override
    public String disassemble(RiMethod method) {
        return "No disassembler available";
    }

    @Override
    public RiConstantPool getConstantPool(RiMethod method) {
        return Compiler.getVMEntries().RiRuntime_getConstantPool(((HotSpotType) method.holder()).klassOop);
    }

    @Override
    public RiOsrFrame getOsrFrame(RiMethod method, int bci) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RiType getRiType(Class< ? > javaClass) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RiSnippets getSnippets() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean mustInline(RiMethod method) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mustNotCompile(RiMethod method) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mustNotInline(RiMethod method) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Object registerTargetMethod(CiTargetMethod targetMethod, String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int sizeofBasicObjectLock() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public RiField getRiField(Field javaField) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RiMethod getRiMethod(Method javaMethod) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RiMethod getRiMethod(Constructor< ? > javaConstructor) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CiConstant invoke(RiMethod method, CiMethodInvokeArguments args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CiConstant foldWordOperation(int opcode, CiMethodInvokeArguments args) {
        // TODO Auto-generated method stub
        return null;
    }

}
