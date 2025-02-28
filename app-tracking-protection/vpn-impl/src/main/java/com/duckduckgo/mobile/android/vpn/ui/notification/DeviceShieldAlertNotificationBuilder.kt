/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.ResultReceiver
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(AppScope::class)
object DeviceShieldAlertNotificationBuilderModule {

    @Provides
    fun providesDeviceShieldAlertNotificationBuilder(
        appBuildConfig: AppBuildConfig,
    ): DeviceShieldAlertNotificationBuilder {
        return AndroidDeviceShieldAlertNotificationBuilder(appBuildConfig)
    }
}

interface DeviceShieldAlertNotificationBuilder {

    fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification

    fun buildAlwaysOnLockdownNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification
}

class AndroidDeviceShieldAlertNotificationBuilder constructor(
    private val appBuildConfig: AppBuildConfig,
) : DeviceShieldAlertNotificationBuilder {

    @Suppress("NewApi") // we use appBuildConfig
    private fun registerAlertChannel(context: Context) {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.getNotificationChannel(VPN_ALERTS_CHANNEL_ID) == null) {
                val channel = NotificationChannel(VPN_ALERTS_CHANNEL_ID, VPN_ALERTS_CHANNEL_NAME, IMPORTANCE_DEFAULT)
                channel.description = VPN_ALERTS_CHANNEL_DESCRIPTION
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification {
        registerAlertChannel(context)

        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_report)

        val notificationImage = getNotificationImage(deviceShieldNotification)
        notificationLayout.setImageViewResource(R.id.deviceShieldNotificationStatusIcon, notificationImage)
        notificationLayout.setTextViewText(R.id.deviceShieldNotificationText, deviceShieldNotification.title)

        return buildNotification(context, notificationLayout, onNotificationPressedCallback, addReportIssueAction = true)
    }

    override fun buildAlwaysOnLockdownNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification {
        registerAlertChannel(context)

        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_report)

        val notificationImage = getNotificationImage(deviceShieldNotification)
        notificationLayout.setImageViewResource(R.id.deviceShieldNotificationStatusIcon, notificationImage)
        notificationLayout.setTextViewText(R.id.deviceShieldNotificationText, deviceShieldNotification.title)

        return buildNotification(context, notificationLayout, onNotificationPressedCallback, addReportIssueAction = false)
    }

    private fun getNotificationImage(deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification): Int {
        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_GOOGLE)) {
            return R.drawable.ic_apptb_google
        }

        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_AMAZON)) {
            return R.drawable.ic_apptb_amazon
        }

        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_FACEBOOK)) {
            return R.drawable.ic_apptb_facebook
        }

        return R.drawable.ic_apptb_default
    }

    private fun buildNotification(
        context: Context,
        content: RemoteViews,
        resultReceiver: ResultReceiver? = null,
        addReportIssueAction: Boolean,
    ): Notification {
        registerAlertChannel(context)

        val vpnControllerIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = resultReceiver)
        val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(vpnControllerIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(vpnControllerPendingIntent)
            .setSilent(true)
            .setCustomContentView(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply {
                if (addReportIssueAction) {
                    addAction(NotificationActionReportIssue.reportIssueNotificationAction(context))
                }
            }
            .build()
    }

    companion object {

        const val VPN_ALERTS_CHANNEL_ID = "com.duckduckgo.mobile.android.vpn.notification.alerts"
        private const val VPN_ALERTS_CHANNEL_NAME = "App Tracking Protection Alerts"
        private const val VPN_ALERTS_CHANNEL_DESCRIPTION = "Alerts from App Tracking Protection"

        private const val TRACKER_COMPANY_GOOGLE = "Google"
        private const val TRACKER_COMPANY_FACEBOOK = "Facebook"
        private const val TRACKER_COMPANY_AMAZON = "Amazon"
    }
}
