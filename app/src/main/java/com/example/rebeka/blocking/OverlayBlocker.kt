package com.example.rebeka.blocking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Экран блокировки как СИСТЕМНОЕ ОКНО, а не Activity.
 *
 * Раньше это была Activity — её всегда можно свернуть кнопкой «домой», и телефон
 * оставался доступен до следующей проверки сервиса. Окно типа TYPE_APPLICATION_OVERLAY
 * висит поверх всего, включая рабочий стол и другие приложения: «домой» уводит
 * launcher под него, но окно остаётся на экране. Убирается только программно —
 * то есть после верного PIN.
 *
 * Требует разрешения SYSTEM_ALERT_WINDOW («Показ поверх других приложений»).
 */
class OverlayBlocker(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: View? = null

    val isShowing: Boolean get() = rootView != null

    @SuppressLint("SetTextI18n")
    fun show(
        remainingText: String,
        onPinSubmit: (pin: String, callback: (Boolean) -> Unit) -> Unit
    ) {
        if (isShowing) return

        // Кастомный FrameLayout нужен, чтобы перехватывать кнопку «назад» —
        // иначе она закроет окно, и блокировка обходится одним нажатием.
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true // проглатываем
                return super.dispatchKeyEvent(event)
            }
        }
        root.setBackgroundColor(Color.parseColor("#F3EFF7"))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(context).apply {
            text = "Время на сегодня закончилось"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(context).apply {
            text = "$remainingText\n\nБольше шагов — больше времени:\n+1 час за каждые 5000 шагов"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val pinInput = EditText(context).apply {
            hint = "PIN родителя"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            textSize = 18f
        }

        val error = TextView(context).apply {
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 16, 0, 0)
        }

        val unlockButton = Button(context).apply {
            text = "Разблокировать"
            setOnClickListener {
                val pin = pinInput.text.toString()
                if (pin.length < 4) {
                    error.text = "PIN не короче 4 цифр"
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                isEnabled = false
                onPinSubmit(pin) { ok ->
                    if (!ok) {
                        error.text = "Неверный PIN"
                        error.visibility = View.VISIBLE
                        pinInput.setText("")
                        isEnabled = true
                    }
                    // При успехе окно снимает сервис — здесь ничего делать не нужно.
                }
            }
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(pinInput)
        content.addView(error)
        content.addView(unlockButton)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Окно фокусируемое — иначе в поле PIN нельзя было бы ничего ввести.
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }

        try {
            windowManager.addView(root, params)
            rootView = root
        } catch (e: Exception) {
            // Нет разрешения «поверх других приложений» — молча выходим,
            // онбординг показывает этот пункт отдельно.
            rootView = null
        }
    }

    fun hide() {
        rootView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        rootView = null
    }
}
