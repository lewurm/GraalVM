/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import org.junit.*;
import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.*;
import org.junit.runners.*;
import org.junit.runners.model.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;
import com.oracle.truffle.sl.test.SLTestRunner.TestCase;

public final class SLTestRunner extends ParentRunner<TestCase> {

    private static final int REPEATS = 10;

    private static final String INPUT_SUFFIX = ".sl";
    private static final String OUTPUT_SUFFIX = ".output";

    private static final String LF = System.getProperty("line.separator");

    public static final class TestCase {
        private final Source input;
        private final String expectedOutput;
        private final Description name;

        public TestCase(Class<?> testClass, String name, Source input, String expectedOutput) {
            this.input = input;
            this.expectedOutput = expectedOutput;
            this.name = Description.createTestDescription(testClass, name);
        }
    }

    private final SourceManager sourceManager = new SourceManager();
    private final List<TestCase> testCases;

    public SLTestRunner(Class<?> runningClass) throws InitializationError {
        super(runningClass);
        try {
            testCases = createTests(runningClass);
        } catch (IOException e) {
            throw new InitializationError(e);
        }
    }

    @Override
    protected Description describeChild(TestCase child) {
        return child.name;
    }

    @Override
    protected List<TestCase> getChildren() {
        return testCases;
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        super.filter(filter);
    }

    protected List<TestCase> createTests(final Class<?> c) throws IOException, InitializationError {
        SLTestSuite suite = c.getAnnotation(SLTestSuite.class);
        if (suite == null) {
            throw new InitializationError(String.format("@%s annotation required on class '%s' to run with '%s'.", SLTestSuite.class.getSimpleName(), c.getName(), SLTestRunner.class.getSimpleName()));
        }

        String[] pathes = suite.value();

        Path root = null;
        for (String path : pathes) {
            root = FileSystems.getDefault().getPath(path);
            if (Files.exists(root)) {
                break;
            }
        }
        if (root == null && pathes.length > 0) {
            throw new FileNotFoundException(pathes[0]);
        }

        final Path rootPath = root;

        final List<TestCase> foundCases = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path inputFile, BasicFileAttributes attrs) throws IOException {
                String name = inputFile.getFileName().toString();
                if (name.endsWith(INPUT_SUFFIX)) {
                    String baseName = name.substring(0, name.length() - INPUT_SUFFIX.length());

                    Path outputFile = inputFile.resolveSibling(baseName + OUTPUT_SUFFIX);
                    if (!Files.exists(outputFile)) {
                        throw new Error("Output file does not exist: " + outputFile);
                    }

                    // fix line feeds for non unix os
                    StringBuilder outFile = new StringBuilder();
                    for (String line : Files.readAllLines(outputFile, Charset.defaultCharset())) {
                        outFile.append(line);
                        outFile.append(LF);
                    }
                    foundCases.add(new TestCase(c, baseName, sourceManager.get(inputFile.toString()), outFile.toString()));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return foundCases;
    }

    @Override
    protected void runChild(TestCase testCase, RunNotifier notifier) {
        notifier.fireTestStarted(testCase.name);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printer = new PrintStream(out);
        PrintStream origErr = System.err;
        try {
            System.setErr(printer);
            SLContext context = new SLContext(sourceManager, printer);
            SLMain.run(context, testCase.input, null, REPEATS);

            String actualOutput = new String(out.toByteArray());

            Assert.assertEquals(repeat(testCase.expectedOutput, REPEATS), actualOutput);
        } catch (AssertionError e) {
            notifier.fireTestFailure(new Failure(testCase.name, e));
        } catch (Throwable ex) {
            notifier.fireTestFailure(new Failure(testCase.name, ex));
        } finally {
            System.setErr(origErr);
            notifier.fireTestFinished(testCase.name);
        }
    }

    private static String repeat(String s, int count) {
        StringBuilder result = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(s);
        }
        return result.toString();
    }

    public static void runInMain(Class<?> testClass, String[] args) throws InitializationError, NoTestsRemainException {
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(System.out));
        SLTestRunner suite = new SLTestRunner(testClass);
        if (args.length > 0) {
            suite.filter(new NameFilter(args[0]));
        }
        Result r = core.run(suite);
        if (!r.wasSuccessful()) {
            System.exit(1);
        }
    }

    private static final class NameFilter extends Filter {
        private final String pattern;

        private NameFilter(String pattern) {
            this.pattern = pattern.toLowerCase();
        }

        @Override
        public boolean shouldRun(Description description) {
            return description.getMethodName().toLowerCase().contains(pattern);
        }

        @Override
        public String describe() {
            return "Filter contains " + pattern;
        }
    }

}
