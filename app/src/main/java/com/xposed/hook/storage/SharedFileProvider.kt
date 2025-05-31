package com.xposed.hook.storage

import android.content.Context
import android.content.pm.ProviderInfo
import android.util.Log
import androidx.core.content.FileProvider

/**
 * Created by lin on 2021/8/21.
 */
class SharedFileProvider : FileProvider() {

    override fun attachInfo(context: Context, info: ProviderInfo) {
        try {
            super.attachInfo(context, info)
        } catch (e: SecurityException) {
            Log.w("SharedFileProvider", "SecurityException caught, using workaround")

            // 创建新的ProviderInfo，避免冲突
            val newInfo = ProviderInfo().apply {
                authority = info.authority.split(";").firstOrNull() ?: info.authority
                packageName = info.packageName
                name = info.name
                exported = false
                grantUriPermissions = info.grantUriPermissions
                readPermission = info.readPermission
                writePermission = info.writePermission
                pathPermissions = info.pathPermissions
                multiprocess = info.multiprocess
                initOrder = info.initOrder
                flags = info.flags
                applicationInfo = info.applicationInfo
                metaData = info.metaData
            }

            try {
                super.attachInfo(context, newInfo)
            } catch (e2: Exception) {
                Log.e("SharedFileProvider", "Workaround failed, using default", e2)
                // 如果还是失败，就不处理SecurityException了
                super.attachInfo(context, info)
            }
        } catch (e: Exception) {
            Log.e("SharedFileProvider", "Unexpected error in attachInfo", e)
            throw e
        }
    }
}