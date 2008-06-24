/*
 * TestUnitTestSuite.java
 * JUnit based test
 *
 * Created on January 15, 2007, 4:06 PM
 */

package org.jruby.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.exceptions.RaiseException;

/**
 *
 * @author headius
 */
public class TestUnitTestSuite extends TestSuite {
    private static final String TEST_DIR = "test";
    private static final String REGEXP_TEST_CASE_SUBCLASS = "^\\s*class\\s+([^\\s]+)\\s*<.*TestCase\\s*$";
    private static final Pattern PATTERN_TEST_CASE_SUBCLASS = Pattern.compile(REGEXP_TEST_CASE_SUBCLASS);

    protected ByteArrayInputStream in;
    protected ByteArrayOutputStream out;
    protected PrintStream printOut;
    protected ByteArrayOutputStream err;
    protected PrintStream printErr;
    protected Ruby runtime;

    /**
     * suite method automatically generated by JUnit module
     */
    public TestUnitTestSuite(String testIndex) throws Exception {
        File testDir;
        if (System.getProperty("basedir") != null) {
            testDir = new File(System.getProperty("basedir"), "target/test-classes/" + TEST_DIR);
        } else {
            testDir = new File(TEST_DIR);
        }

        File testIndexFile = new File(testDir, testIndex);

        if (!testIndexFile.canRead()) {
            // Since we don't have any other error reporting mechanism, we
            // add the error message as an always-failing test to the test suite.
            addTest(new FailingTest("TestUnitTestSuite",
                                          "Couldn't locate " + testIndex +
                                          ". Make sure you run the tests from the base " +
                                          "directory of the JRuby sourcecode."));
            return;
        }

        BufferedReader testFiles =
            new BufferedReader(new InputStreamReader(new FileInputStream(testIndexFile)));

        String line;
        while ((line = testFiles.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#") || line.length() == 0) {
                continue;
            }

            addTest(new ScriptTest(line, testDir));
        }
        
        setUp();
    }

    protected void setUp() throws Exception {
        in = new ByteArrayInputStream(new byte[0]);
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        runtime = Ruby.newInstance(in, printOut = new PrintStream(out), printErr = new PrintStream(err));
        setupInterpreter(runtime);
    }

    protected void tearDown() throws Exception {
        in.close();
        out.close();
        err.close();
        printOut.close();
        printErr.close();

        in = null;
        out = null;
        err = null;
        printOut = null;
        printErr = null;
        runtime = null;
    }

    private void setupInterpreter(Ruby runtime) {
        ArrayList loadPath = new ArrayList();

        loadPath.add("test/externals/bfts");
        loadPath.add("test/externals/ruby_test/lib");

        runtime.getLoadService().init(loadPath);
        runtime.defineGlobalConstant("ARGV", runtime.newArray());
    }

    private class ScriptTest extends TestCase {
        private final String filename;
        private final File testDir;

        public ScriptTest(String filename, File dir) {
            super(filename);
            this.filename = filename;
            this.testDir = dir;
        }


        private String scriptName() {
            return new File(testDir, filename).getPath();
        }

        private String pretty(List list) {
            StringBuffer prettyOut = new StringBuffer();

            for (Iterator iter = list.iterator(); iter.hasNext();) {
                prettyOut.append(iter.next().toString());
            }

            return prettyOut.toString();
        }

        private List<String> getTestClassNamesFromReadingTestScript(String filename) {
            List<String> testClassNames = new ArrayList<String>();
            File f = new File(TEST_DIR + "/" + filename + ".rb");
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(f));
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    Matcher m = PATTERN_TEST_CASE_SUBCLASS.matcher(line);
                    if (m.find())
                    {
                        testClassNames.add(m.group(1));
                    }
                }
                if (!testClassNames.isEmpty()) {
                    return testClassNames;
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        throw new RuntimeException("Could not close reader!", e);
                    }
                }

            }
            throw new RuntimeException("No *TestCase derivative found in '" + filename + ".rb'!");
        }

        public void runTest() throws Throwable {
            StringBuffer script = new StringBuffer();

            List<String> testClassNames = getTestClassNamesFromReadingTestScript(filename);

            // there might be more test classes in a single file, so we iterate over them
            for (String testClass : testClassNames) {
                try {
                    script.append("require 'test/junit_testrunner.rb'\n");
                    script.append("require '" + scriptName() + "'\n");
                    script.append("runner = Test::Unit::UI::JUnit::TestRunner.new(" + testClass + ")\n");
                    script.append("runner.start\n");
                    script.append("runner.faults\n");
    
                    RubyArray faults = (RubyArray)runtime.executeScript(script.toString(), scriptName() + "_generated_test.rb");
    
                    if (!faults.isEmpty()) {
                        StringBuffer faultString = new StringBuffer("Faults encountered running " + scriptName() + ", complete output follows:\n");
                        for (Iterator iter = faults.iterator(); iter.hasNext();) {
                            String fault = iter.next().toString();
    
                            faultString.append(fault).append("\n");
                        }
    
                        fail(faultString.toString());
                    }
                } catch (RaiseException re) {
                    fail("Faults encountered running " + scriptName() + ", complete output follows:\n" + re.getException().message + "\n" + pretty(((RubyArray)re.getException().backtrace()).getList()));
                }
            }
        }
    }
}
