package com.oyenter.lo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oyenter.lo.LoApp

/** Кнопки виджета. Работают и при убитом процессе: LoApp поднимет контроллер и выполнит действие. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? LoApp ?: return
        when (intent.action) {
            "com.oyenter.lo.widget.TOGGLE" -> app.player.toggle()
            "com.oyenter.lo.widget.NEXT" -> app.player.next()
            "com.oyenter.lo.widget.PREV" -> app.player.previous()
        }
        LoWidget.refresh(context)
    }
}
