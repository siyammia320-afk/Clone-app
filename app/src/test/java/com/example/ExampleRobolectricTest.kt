package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ApkCloneBuilder
import com.example.engine.JarApkSigner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OmniSpace Dual Cloner", appName)
  }

  @Test
  fun `generate distinct cloned package names for 10x clones`() {
    val basePkg = "com.whatsapp"
    val clone1 = ApkCloneBuilder.generateClonedPackageName(basePkg, 1)
    val clone10 = ApkCloneBuilder.generateClonedPackageName(basePkg, 10)
    assertEquals("com.whatsapp.clone1", clone1)
    assertEquals("com.whatsapp.clone10", clone10)
    assertNotEquals(clone1, clone10)
  }

  @Test
  fun `build and sign standalone cloned APK with BinaryXmlPatcher and JarApkSigner`() {
    val sourceApk = File("build/outputs/apk/debug/app-debug.apk")
    if (!sourceApk.exists()) return
    val outApk = File("/tmp/verified_clone1.apk")
    JarApkSigner.buildAndSignClonedApk(
      sourceApk = sourceApk,
      splitApks = emptyList(),
      destApk = outApk,
      originalPackage = "com.aistudio.omnispace.cloner",
      newPackage = "com.aistudio.omnispace.cloner.clone1",
      customAppLabel = "OmniSpace #1",
      cloneNumber = 1
    )
    assertTrue(outApk.exists())
    assertTrue(outApk.length() > 100_000L)
  }
}
