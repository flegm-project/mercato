package com.mercato.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The remove-ads entitlement is a Play Billing purchase mirrored into a
 * preference. If that preference travels in the device backup, restoring a
 * backup grants remove-ads for free and forever, offline, because restore()
 * never revokes without a store round-trip. The fix keeps allowBackup true so
 * the player keeps progression, stats, onboarding, consent and settings, and
 * excludes only the entitlement's own DataStore file from every backup path.
 *
 * DataStore preferences persist at files/datastore/<name>.preferences_pb, and
 * backup rules address files relative to getFilesDir(), so the excluded path is
 * datastore/<ENTITLEMENT_STORE>.preferences_pb. These are plain XML resources,
 * so this is a JVM unit test that reads them straight off disk; it fails before
 * the split (files absent, key still in the main store) and passes after.
 */
class BackupRulesTest {

    private val entitlementFile = "datastore/${Prefs.ENTITLEMENT_STORE}.preferences_pb"
    private val mainFile = "datastore/${Prefs.MAIN_STORE}.preferences_pb"

    @Test
    fun entitlementAndMainStoreAreDistinct() {
        // A shared store cannot be excluded without also dropping progression.
        assertFalse(
            "the entitlement must live in its own DataStore, not the main one",
            Prefs.ENTITLEMENT_STORE == Prefs.MAIN_STORE,
        )
    }

    @Test
    fun autoBackupExcludesTheEntitlement() {
        val excluded = fileExcludes(resXml("backup_rules.xml"), section = null)
        assertTrue(
            "backup_rules.xml must exclude $entitlementFile, got $excluded",
            excluded.contains(entitlementFile),
        )
        assertFalse(
            "the main store carries progression and must stay in the backup",
            excluded.contains(mainFile),
        )
    }

    @Test
    fun dataExtractionExcludesTheEntitlementFromCloudAndTransfer() {
        val doc = resXml("data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val excluded = fileExcludes(doc, section)
            assertTrue(
                "data_extraction_rules.xml <$section> must exclude $entitlementFile, got $excluded",
                excluded.contains(entitlementFile),
            )
            assertFalse(
                "<$section> must keep the main store so progression transfers",
                excluded.contains(mainFile),
            )
        }
    }

    /** File-domain exclude paths, optionally restricted to one parent section. */
    private fun fileExcludes(xml: File, section: String?): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val nodes = doc.getElementsByTagName("exclude")
        val paths = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("domain") != "file") continue
            if (section != null && (el.parentNode as? Element)?.tagName != section) continue
            paths += el.getAttribute("path")
        }
        return paths
    }

    /** Unit tests run with the module as working dir; walk up to be safe. */
    private fun resXml(name: String): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val here = dir ?: return@repeat
            for (candidate in listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name")) {
                val f = File(here, candidate)
                if (f.exists()) return f
            }
            dir = here.parentFile
        }
        fail("could not locate res/xml/$name from ${System.getProperty("user.dir")}")
        error("unreachable")
    }
}
