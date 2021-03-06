/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.loop.InductionVariable.Direction;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;

public class LoopEx {

    private final Loop<Block> loop;
    private LoopFragmentInside inside;
    private LoopFragmentWhole whole;
    private CountedLoopInfo counted; // TODO (gd) detect
    private LoopsData data;
    private InductionVariables ivs;

    LoopEx(Loop<Block> loop, LoopsData data) {
        this.loop = loop;
        this.data = data;
    }

    public Loop<Block> loop() {
        return loop;
    }

    public LoopFragmentInside inside() {
        if (inside == null) {
            inside = new LoopFragmentInside(this);
        }
        return inside;
    }

    public LoopFragmentWhole whole() {
        if (whole == null) {
            whole = new LoopFragmentWhole(this);
        }
        return whole;
    }

    public void invalidateFragments() {
        inside = null;
        whole = null;
    }

    @SuppressWarnings("unused")
    public LoopFragmentInsideFrom insideFrom(FixedNode point) {
        // TODO (gd)
        return null;
    }

    @SuppressWarnings("unused")
    public LoopFragmentInsideBefore insideBefore(FixedNode point) {
        // TODO (gd)
        return null;
    }

    public boolean isOutsideLoop(Node n) {
        return !whole().contains(n);
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) loop().getHeader().getBeginNode();
    }

    public FixedNode predecessor() {
        return (FixedNode) loopBegin().forwardEnd().predecessor();
    }

    public FixedNode entryPoint() {
        return loopBegin().forwardEnd();
    }

    public boolean isCounted() {
        return counted != null;
    }

    public CountedLoopInfo counted() {
        return counted;
    }

    public LoopEx parent() {
        if (loop.getParent() == null) {
            return null;
        }
        return data.loop(loop.getParent());
    }

    public int size() {
        return whole().nodes().count();
    }

    @Override
    public String toString() {
        return (isCounted() ? "CountedLoop [" + counted() + "] " : "Loop ") + "(depth=" + loop().getDepth() + ") " + loopBegin();
    }

    private class InvariantPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return isOutsideLoop(n);
        }
    }

    public void reassociateInvariants() {
        InvariantPredicate invariant = new InvariantPredicate();
        StructuredGraph graph = loopBegin().graph();
        for (BinaryArithmeticNode<?> binary : whole().nodes().filter(BinaryArithmeticNode.class)) {
            if (!binary.isAssociative()) {
                continue;
            }
            BinaryArithmeticNode<?> result = BinaryArithmeticNode.reassociate(binary, invariant, binary.getX(), binary.getY());
            if (result != binary) {
                if (Debug.isLogEnabled()) {
                    Debug.log("%s : Reassociated %s into %s", graph.method().format("%H::%n"), binary, result);
                }
                if (!result.isAlive()) {
                    assert !result.isDeleted();
                    result = graph.addOrUniqueWithInputs(result);
                }
                binary.replaceAtUsages(result);
                GraphUtil.killWithUnusedFloatingInputs(binary);
            }
        }
    }

    public boolean detectCounted() {
        LoopBeginNode loopBegin = loopBegin();
        FixedNode next = loopBegin.next();
        while (next instanceof FixedGuardNode || next instanceof ValueAnchorNode) {
            next = ((FixedWithNextNode) next).next();
        }
        if (next instanceof IfNode) {
            IfNode ifNode = (IfNode) next;
            boolean negated = false;
            if (!loopBegin.isLoopExit(ifNode.falseSuccessor())) {
                if (!loopBegin.isLoopExit(ifNode.trueSuccessor())) {
                    return false;
                }
                negated = true;
            }
            LogicNode ifTest = ifNode.condition();
            if (!(ifTest instanceof IntegerLessThanNode)) {
                if (ifTest instanceof IntegerBelowNode) {
                    Debug.log("Ignored potential Counted loop at %s with |<|", loopBegin);
                }
                return false;
            }
            IntegerLessThanNode lessThan = (IntegerLessThanNode) ifTest;
            Condition condition = null;
            InductionVariable iv = null;
            ValueNode limit = null;
            if (isOutsideLoop(lessThan.getX())) {
                iv = getInductionVariables().get(lessThan.getY());
                if (iv != null) {
                    condition = lessThan.condition().mirror();
                    limit = lessThan.getX();
                }
            } else if (isOutsideLoop(lessThan.getY())) {
                iv = getInductionVariables().get(lessThan.getX());
                if (iv != null) {
                    condition = lessThan.condition();
                    limit = lessThan.getY();
                }
            }
            if (condition == null) {
                return false;
            }
            if (negated) {
                condition = condition.negate();
            }
            boolean oneOff = false;
            switch (condition) {
                case LE:
                    oneOff = true; // fall through
                case LT:
                    if (iv.direction() != Direction.Up) {
                        return false;
                    }
                    break;
                case GE:
                    oneOff = true; // fall through
                case GT:
                    if (iv.direction() != Direction.Down) {
                        return false;
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
            counted = new CountedLoopInfo(this, iv, limit, oneOff, negated ? ifNode.falseSuccessor() : ifNode.trueSuccessor());
            return true;
        }
        return false;
    }

    public LoopsData loopsData() {
        return data;
    }

    public NodeBitMap nodesInLoopFrom(BeginNode point, BeginNode until) {
        Collection<BeginNode> blocks = new LinkedList<>();
        Collection<LoopExitNode> exits = new LinkedList<>();
        Queue<Block> work = new LinkedList<>();
        ControlFlowGraph cfg = loopsData().controlFlowGraph();
        work.add(cfg.blockFor(point));
        Block untilBlock = until != null ? cfg.blockFor(until) : null;
        while (!work.isEmpty()) {
            Block b = work.remove();
            if (b == untilBlock) {
                continue;
            }
            if (loop().getExits().contains(b)) {
                exits.add((LoopExitNode) b.getBeginNode());
            } else if (loop().getBlocks().contains(b)) {
                blocks.add(b.getBeginNode());
                work.addAll(b.getDominated());
            }
        }
        return LoopFragment.computeNodes(point.graph(), blocks, exits);
    }

    public InductionVariables getInductionVariables() {
        if (ivs == null) {
            ivs = new InductionVariables(this);
        }
        return ivs;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        if (ivs != null) {
            ivs.deleteUnusedNodes();
        }
    }
}
