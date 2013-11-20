/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.api.meta.MetaUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;

/**
 * Base class for a stub defined by a snippet.
 */
public abstract class SnippetStub extends Stub implements Snippets {

    static class Template extends AbstractTemplates {

        Template(Providers providers, TargetDescription target, Class<? extends Snippets> declaringClass) {
            super(providers, target);
            this.info = snippet(declaringClass, null);
        }

        /**
         * Info for the method implementing the stub.
         */
        protected final SnippetInfo info;

        protected StructuredGraph getGraph(Arguments args) {
            SnippetTemplate template = template(args);
            return template.copySpecializedGraph();
        }
    }

    protected final Template snippet;

    /**
     * Creates a new snippet stub.
     * 
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(providers, linkage);
        this.snippet = new Template(providers, target, getClass());
    }

    @Override
    protected StructuredGraph getGraph() {
        return snippet.getGraph(makeArguments(snippet.info));
    }

    /**
     * Adds the arguments to this snippet stub.
     */
    protected Arguments makeArguments(SnippetInfo stub) {
        Arguments args = new Arguments(stub, GuardsStage.FLOATING_GUARDS);
        for (int i = 0; i < stub.getParameterCount(); i++) {
            String name = stub.getParameterName(i);
            if (stub.isConstantParameter(i)) {
                args.addConst(name, getConstantParameterValue(i, name));
            } else {
                assert !stub.isVarargsParameter(i);
                args.add(name, null);
            }
        }
        return args;
    }

    protected Object getConstantParameterValue(int index, String name) {
        throw new GraalInternalError("%s must override getConstantParameterValue() to provide a value for parameter %d%s", getClass().getName(), index, name == null ? "" : " (" + name + ")");
    }

    @Override
    protected Object debugScopeContext() {
        return getInstalledCodeOwner();
    }

    @Override
    public ResolvedJavaMethod getInstalledCodeOwner() {
        return snippet.info.getMethod();
    }

    @Override
    public String toString() {
        return "Stub<" + format("%h.%n", getInstalledCodeOwner()) + ">";
    }
}
