/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Shopify Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.shopify.testify

import android.app.Instrumentation
import android.content.Context
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import com.shopify.testify.internal.output.OutputFileUtility
import com.shopify.testify.report.ReportSession
import com.shopify.testify.report.Reporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.Description
import java.io.File

internal open class ReporterTest {

    private val mockContext: Context = mock()
    private val mockSession: ReportSession = mock()
    private val mockOutputFileUtility: OutputFileUtility = mock()
    private val mockInstrumentation: Instrumentation = mock()
    private val mockRule: ScreenshotRule<*> = mock()
    private val mockDescription: Description = mock()
    private val mockFile: File = mock()
    private val reporter = spy(Reporter(mockContext, mockSession, mockOutputFileUtility))

//    @Before
    fun setup() {
        with(mockSession) {
            doReturn("SESSION-ID").whenever(this).sessionId
            doReturn("Today").whenever(this).getTimestamp(any())
            doReturn(1).whenever(this).failCount
            doReturn(2).whenever(this).passCount
            doReturn(3).whenever(this).testCount
        }

        with(mockOutputFileUtility) {
            doReturn(false).whenever(this).useSdCard()
        }

        with(mockContext) {
            doReturn(File("foo")).whenever(this).getExternalFilesDir(any())
            doReturn(File("/data/data/com.app.example/app_testify")).whenever(this).getDir(eq("testify"), any())
            doReturn(File("/sdcard")).whenever(this).getExternalFilesDir(anyOrNull())
        }

        with(reporter) {
            doReturn("foo").whenever(this).getBaselinePath(any())
            doReturn("bar").whenever(this).getOutputPath(any())
        }

        with(mockInstrumentation) {
            doReturn(mockContext).whenever(this).context
        }

        with(mockRule) {
            doReturn("startTest").whenever(this).testMethodName
        }

        with(mockDescription) {
            doReturn(ReporterTest::class.java).whenever(this).testClass
        }
    }

    private fun setupMockFile() {
        doReturn(true).whenever(mockFile).exists()

        with(reporter) {
            doReturn(mockFile).whenever(this).getReportFile()
            doNothing().whenever(this).writeToFile(any(), any())
            doNothing().whenever(this).clearFile(eq(mockFile))
            doReturn(BODY_LINES).whenever(this).readBodyLines(mockFile)
        }

        with(mockSession) {
            doReturn(true).whenever(this).isEqual(eq(mockFile))
            val lines = HEADER_LINES + BODY_LINES
            doAnswer {
                this.initFromLines(lines)
            }.whenever(this).initFromFile(eq(mockFile))
        }
    }

    @Test
    fun `writeHeader() produces the expected yaml`() {
        reporter.insertHeader()
        assertEquals(FILE_HEADER, reporter.yaml)
    }

    @Test
    fun `startTest() produces the expected yaml`() {
        reporter.startTest(mockRule, mockDescription)

        assertEquals(
            "    - test:\n" +
                    "        name: startTest\n" +
                    "        class: ReporterTest\n" +
                    "        package: com.shopify.testify\n",
            reporter.yaml
        )
    }

    @Test
    fun `captureOutput() produces the expected yaml`() {
        reporter.captureOutput(mockRule)
        assertEquals(
            "        baseline_image: assets/foo\n" +
                    "        test_image: bar\n",
            reporter.yaml
        )
    }

    @Test
    fun `pass() produces the expected yaml`() {
        reporter.pass()
        assertEquals("        status: PASS\n", reporter.yaml)
    }

    @Test
    fun `fail() produces the expected yaml`() {
        reporter.fail(Exception("Custom description"))
        assertEquals(
            "        status: FAIL\n" +
                    "        cause: UNKNOWN\n" +
                    "        description: \"Custom description\"\n",
            reporter.yaml
        )
    }

    @Test
    fun `endTest() produces the expected yaml for a new session`() {
        setupMockFile()
        doReturn(false).whenever(mockFile).exists()

        reporter.endTest()

        assertEquals(FILE_HEADER, reporter.yaml)
    }

    @Test
    fun `endTest() produces the expected yaml for an existing session`() {
        setupMockFile()

        reporter.endTest()

        assertEquals(FILE_HEADER +
                "  - test:\n" +
                "    name: default\n" +
                "    class: ClientDetailsViewScreenshotTest\n" +
                "    package: com.shopify.testify.sample.clients.details\n" +
                "    baseline_image: assets/screenshots/22-480x800@240dp-en_US/default.png\n" +
                "    test_image: /data/data/com.shopify.testify.sample/app_images/screenshots/22-480x800@240dp-en_US/ClientDetailsViewScreenshotTest_default.png\n" +
                "    status: PASS\n", reporter.yaml)
    }

    @Test
    fun `endTest() produces the expected yaml for when overwriting a different session`() {
        setupMockFile()
        doReturn(false).whenever(mockSession).isEqual(eq(mockFile))

        reporter.endTest()

        assertEquals(FILE_HEADER, reporter.yaml)
    }

    @Test
    fun `output file path when not using sdcard`() {
        val file = reporter.getReportFile()
        assertEquals("/data/data/com.app.example/app_testify/report.yml", file.path)
    }

    @Test
    fun `output file path when using sdcard`() {
        doReturn(true).whenever(mockOutputFileUtility).useSdCard()
        val file = reporter.getReportFile()
        assertEquals("/sdcard/testify/report.yml", file.path)
    }

    @Test
    fun `reporter output for a single test in a new session`() {
        setupMockFile()
        doReturn(false).whenever(mockFile).exists()

        reporter.startTest(mockRule, mockDescription)
        reporter.identifySession(mockInstrumentation)
        reporter.captureOutput(mockRule)
        reporter.pass()
        reporter.endTest()

        val yaml = reporter.yaml

        assertEquals("---\n" +
                "- session: SESSION-ID\n" +
                "- date: Today\n" +
                "- failed: 1\n" +
                "- passed: 2\n" +
                "- total: 3\n" +
                "- tests:\n" +
                "    - test:\n" +
                "        name: startTest\n" +
                "        class: ReporterTest\n" +
                "        package: com.shopify.testify\n" +
                "        baseline_image: assets/foo\n" +
                "        test_image: bar\n" +
                "        status: PASS\n", yaml)
    }

    @Test
    fun `reporter output for a multiples tests in a new session`() {

        val session = spy(ReportSession())
        val reporter = spy(Reporter(mockContext, session, mockOutputFileUtility))

        with(mockOutputFileUtility) {
            doReturn(false).whenever(this).useSdCard()
        }

        with(mockContext) {
            doReturn(File("foo")).whenever(this).getExternalFilesDir(any())
            doReturn(File("/data/data/com.app.example/app_testify")).whenever(this).getDir(eq("testify"), any())
            doReturn(File("/sdcard")).whenever(this).getExternalFilesDir(anyOrNull())
        }

        with(mockInstrumentation) {
            doReturn(mockContext).whenever(this).context
        }

        with(mockRule) {
            doReturn("startTest").whenever(this).testMethodName
        }

        with(mockDescription) {
            doReturn(ReporterTest::class.java).whenever(this).testClass
        }

        with(reporter) {
            doReturn("foo").whenever(this).getBaselinePath(any())
            doReturn("bar").whenever(this).getOutputPath(any())
            doReturn(mockFile).whenever(this).getReportFile()
            doNothing().whenever(this).writeToFile(any(), any())
            doNothing().whenever(this).clearFile(eq(mockFile))
        }

        doReturn(false).whenever(mockFile).exists()

        reporter.startTest(mockRule, mockDescription)
        reporter.identifySession(mockInstrumentation)
        reporter.captureOutput(mockRule)
        reporter.pass()
        reporter.endTest()

        // Set up for second test
        doReturn(true).whenever(mockFile).exists()
        doReturn(true).whenever(session).isEqual(any())

        val BODY_LINES = listOf(
            "  - test:",
            "    name: startTest",
            "    class: ReporterTest",
            "    package: com.shopify.testify",
            "    baseline_image: assets/foo",
            "    test_image: bar",
            "    status: PASS"
        )

        doReturn(BODY_LINES).whenever(reporter).readBodyLines(mockFile)

        with(session) {
            doReturn(true).whenever(this).isEqual(eq(mockFile))
            val lines = listOf(
                "---",
                "- session: 623815995-1",
                "- date: 2020-06-26@14:49:45",
                "- failed: 0",
                "- passed: 1",
                "- total: 1",
                "- tests:"
            ) + BODY_LINES

            doAnswer {
                this.initFromLines(lines)
            }.whenever(this).initFromFile(eq(mockFile))
        }

        reporter.startTest(mockRule, mockDescription)
        reporter.identifySession(mockInstrumentation)
        reporter.captureOutput(mockRule)
        doReturn("failingTest").whenever(mockRule).testMethodName
        reporter.fail(Exception("This is a failure"))
        reporter.endTest()

        val yaml = reporter.yaml

        val lines = yaml.lines()

        assertEquals("---", lines[0])
        assertTrue("- session: [0-9a-fA-F]{8}-[0-9]{1,3}".toRegex().containsMatchIn(lines[1]))
        assertTrue("- date: [0-9]{4}-[0-9]{2}-[0-9]{2}@[0-9]{2}:[0-9]{2}:[0-9]{2}".toRegex().containsMatchIn(lines[2]))
        assertEquals("- failed: 1", lines[3])
        assertEquals("- passed: 1", lines[4])
        assertEquals("- total: 2", lines[5])
        assertEquals("- tests:", lines[6])
        assertEquals("    - test:", lines[7])
        assertEquals("        name: startTest", lines[8])
        assertEquals("        class: ReporterTest", lines[9])
        assertEquals("        package: com.shopify.testify", lines[10])
        assertEquals("        baseline_image: assets/foo", lines[11])
        assertEquals("        test_image: bar", lines[12])
        assertEquals("        status: PASS", lines[13])
    }

    private val Reporter.yaml: String
        get() {
            return this.builder.toString()
        }

    companion object {
        private const val FILE_HEADER = "---\n" +
                "- session: SESSION-ID\n" +
                "- date: Today\n" +
                "- failed: 1\n" +
                "- passed: 2\n" +
                "- total: 3\n" +
                "- tests:\n"

        private val HEADER_LINES = listOf(
            "---",
            "- session: 623815995-477",
            "- date: 2020-06-26@14:49:45",
            "- failed: 1",
            "- passed: 3",
            "- total: 4",
            "- tests:"
        )

        private val BODY_LINES = listOf(
            "  - test:",
            "    name: default",
            "    class: ClientDetailsViewScreenshotTest",
            "    package: com.shopify.testify.sample.clients.details",
            "    baseline_image: assets/screenshots/22-480x800@240dp-en_US/default.png",
            "    test_image: /data/data/com.shopify.testify.sample/app_images/screenshots/22-480x800@240dp-en_US/ClientDetailsViewScreenshotTest_default.png",
            "    status: PASS"
        )
    }
}
