package com.zynergylabs.forager.app

import android.app.Application
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * Lint dispatch 2026-10-17-03, Phase 2 (RECORD -1195), DataExtractionRules. The owner's standing ruling (the comment
 * above `<application>` in `AndroidManifest.xml`, 2026-09-09) is that Android's Auto Backup **and device-to-device
 * transfer** are off. `android:allowBackup="false"` does that up to Android 11; from Android 12 on, lint says it no
 * longer stops device-to-device transfer, which follows `android:dataExtractionRules` instead. So the app declares
 * rules that exclude every storage domain from both cloud backup and device transfer.
 *
 * Read from the **merged** manifest the build produced (the one that ships in the debug APK, located through the
 * `test_config.properties` the Android Gradle plugin writes for unit tests), and from the rules resource it names, as
 * the app's resources compiled it. Before the fix the manifest names no rules file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndTransferRulesTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun mergedManifestApplication(): Element {
        val stream = javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")
        assertNotNull("the build's test_config.properties is on the test classpath", stream)
        val path = Properties().apply { stream.use { load(it) } }.getProperty("android_merged_manifest")
        assertNotNull("test_config.properties names the merged manifest", path)
        val file = File(path)
        assertTrue("the merged manifest exists at $file", file.isFile)
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        return document.getElementsByTagName("application").item(0) as Element
    }

    @Test
    fun `backup stays off in the merged manifest`() {
        val application = mergedManifestApplication()
        assertEquals("false", application.getAttributeNS(androidNs, "allowBackup"))
        // Android 11 and below: allowBackup="false" already turns every form off; fullBackupContent="false" says the
        // same in the attribute lint reads for those versions (lint DataExtractionRules, second form).
        assertEquals("false", application.getAttributeNS(androidNs, "fullBackupContent"))
    }

    @Test
    fun `the merged manifest names data extraction rules that exclude every domain from cloud backup and device transfer`() {
        val reference = mergedManifestApplication().getAttributeNS(androidNs, "dataExtractionRules")
        assertTrue("android:dataExtractionRules is declared and names an XML resource, was '$reference'", reference.startsWith("@xml/"))

        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = reference.removePrefix("@xml/")
        // Looked up by name: the name comes from the manifest under test, so it cannot be a compiled R reference.
        val id = context.resources.getIdentifier(name, "xml", context.packageName)
        assertTrue("the resource $reference exists", id != 0)

        val sections = readRules(context.resources.getXml(id))
        assertEquals("both sections are present", setOf("cloud-backup", "device-transfer"), sections.keys)
        for ((section, rules) in sections) {
            assertEquals("$section includes nothing", emptyList<String>(), rules.includes)
            assertEquals("$section excludes every domain whole", ALL_DOMAINS, rules.wholeDomainsExcluded)
        }
    }

    private class SectionRules(val includes: MutableList<String> = mutableListOf(), val wholeDomainsExcluded: MutableSet<String> = mutableSetOf())

    private fun readRules(parser: XmlResourceParser): Map<String, SectionRules> {
        val sections = mutableMapOf<String, SectionRules>()
        var current: SectionRules? = null
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlResourceParser.START_TAG -> when (parser.name) {
                    "cloud-backup", "device-transfer" -> current = SectionRules().also { sections[parser.name] = it }
                    "include" -> current!!.includes += "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
                    "exclude" -> if (parser.getAttributeValue(null, "path") == ".") {
                        current!!.wholeDomainsExcluded += parser.getAttributeValue(null, "domain")
                    }
                }
                XmlResourceParser.END_TAG -> if (parser.name == "cloud-backup" || parser.name == "device-transfer") current = null
            }
        }
        parser.close()
        return sections
    }

    private companion object {
        /** Every domain Android's data extraction rules accept, as the platform documents them (my reading; not re-checked against the platform source). */
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
