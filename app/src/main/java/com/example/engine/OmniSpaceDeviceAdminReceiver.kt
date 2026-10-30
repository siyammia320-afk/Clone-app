package com.example.engine

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.UserManager
import android.widget.Toast

class OmniSpaceDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val componentName = ComponentName(context.applicationContext, OmniSpaceDeviceAdminReceiver::class.java)
        runCatching {
            dpm?.setProfileName(componentName, "OmniSpace Dual Profile")
            dpm?.setProfileEnabled(componentName)
        }
        Toast.makeText(context, "OmniSpace Native Dual Profile Activated!", Toast.LENGTH_LONG).show()
    }
}

data class SystemDualProfileStatus(
    val isWorkProfileActive: Boolean,
    val isProvisioningSupported: Boolean,
    val profileUsersCount: Int,
    val crossProfileLabel: String
)

object WorkProfileHelper {
    fun checkStatus(context: Context): SystemDualProfileStatus {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val component = ComponentName(context, OmniSpaceDeviceAdminReceiver::class.java)

        val isProfileOwner = runCatching {
            dpm?.isProfileOwnerApp(context.packageName) == true || dpm?.isAdminActive(component) == true
        }.getOrDefault(false)

        val userProfilesCount = runCatching {
            userManager?.userProfiles?.size ?: 1
        }.getOrDefault(1)

        val crossProfileLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val crossProfileApps = context.getSystemService(CrossProfileApps::class.java)
            val targets = runCatching { crossProfileApps?.targetUserProfiles ?: emptyList() }.getOrDefault(emptyList())
            if (targets.isNotEmpty()) {
                "Hardware Dual Profile Linked (${targets.size} secondary profile)"
            } else {
                "Ready for Managed Work Profile or Unlimited APK Cloner"
            }
        } else {
            "Ready for Multi-Space APK Cloner"
        }

        val canProvision = runCatching {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)

        return SystemDualProfileStatus(
            isWorkProfileActive = isProfileOwner || userProfilesCount > 1,
            isProvisioningSupported = canProvision,
            profileUsersCount = userProfilesCount,
            crossProfileLabel = crossProfileLabel
        )
    }

    fun launchWorkProfileProvisioning(context: Context) {
        val component = ComponentName(context, OmniSpaceDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                component
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Managed Work Profile setup is not supported by this ROM/emulator. Use the Unlimited APK Package Cloner instead!",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
