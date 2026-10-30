package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ApkCloneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    val basePkg = "com.whatsapp.messenger"
    val clone1 = ApkCloneBuilder.generateClonedPackageName(basePkg, 1)
    val clone10 = ApkCloneBuilder.generateClonedPackageName(basePkg, 10)
    assertNotEquals(basePkg, clone1)
    assertNotEquals(clone1, clone10)
    assertEquals(basePkg.length, clone1.length)
    assertEquals(basePkg.length, clone10.length)
  }
}
