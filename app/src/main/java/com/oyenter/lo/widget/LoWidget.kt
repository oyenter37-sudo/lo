package com.oyenter.lo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.oyenter.lo.LoApp
import com.oyenter.lo.MainActivity
import com.oyenter.lo.R

class LoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.oyenter.lo.widget.TOGGLE"
        private const val ACTION_NEXT = "com.oyenter.lo.widget.NEXT"
        private const val ACTION_PREV = "com.oyenter.lo.widget.PREV"

        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, LoWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun build(context: Context): RemoteViews {
            val app = context.applicationContext as? LoApp
            val state = app?.player?.state?.value
            val views = RemoteViews(context.packageName, R.layout.widget_lo)

            views.setTextViewText(
                R.id.widget_title,
                state?.current?.title ?: "Lo",
            )
            views.setTextViewText(
                R.id.widget_subtitle,
                state?.current?.artist?.ifBlank { "тихий плеер" } ?: "нажми, чтобы открыть",
            )
            views.setImageViewResource(
                R.id.widget_toggle,
                if (state?.isPlaying == true) R.drawable.ic_w_pause else R.drawable.ic_w_play,
            )

            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 11))
            views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, ACTION_TOGGLE, 12))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 13))
            views.setOnClickPendingIntent(R.id.widget_body, openApp(context))
            return views
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
