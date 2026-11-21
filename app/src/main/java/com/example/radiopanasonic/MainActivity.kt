package com.example.radiopanasonic

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private var ir: ConsumerIrManager? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private lateinit var freqText: TextView
    private lateinit var status: TextView
    private lateinit var curInput: EditText
    private lateinit var gotoInput: EditText
    private lateinit var stepInput: EditText
    private lateinit var gapInput: EditText

    private lateinit var labGenre: EditText
    private lateinit var labId: EditText
    private lateinit var labFrom: EditText
    private lateinit var labTo: EditText
    private lateinit var labDelay: EditText
    private lateinit var labNibble: CheckBox

    private var freq = 88.1
    @Volatile private var busy = false
    @Volatile private var cancel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("radio", Context.MODE_PRIVATE)
        freq = prefs.getFloat("freq", 88.1f).toDouble()
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        buildUi()
        showFreq()
        if (ir?.hasIrEmitter() != true) {
            status.text = "Este celular NO tiene emisor infrarrojo."
        } else {
            status.text = "Emisor IR listo. Apuntalo al equipo."
        }
    }

    // ---------------------------------------------------------------- IR

    private fun send(c: Code) = send(c.genre, c.id, c.cmd)

    private fun send(genre: Int, id: Int, cmd: Int) {
        val m = ir ?: return
        try {
            m.transmit(Kaseikyo.CARRIER, Kaseikyo.frame(genre, id, cmd))
        } catch (e: Exception) {
            runOnUiThread { toast("Error IR: ${e.message}") }
        }
    }

    // ---------------------------------------------------------------- UI helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun label(text: String, size: Float = 14f, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (v in views) {
                addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    /** Boton que repite el codigo mientras se mantiene apretado. */
    private fun holdButton(
        text: String,
        c: Code,
        fast: Boolean = false,
        onSend: (() -> Unit)? = null
    ): Button {
        val fire: () -> Unit = {
            send(c)
            onSend?.invoke()
        }
        val period = if (fast) 40L else 150L
        val first = if (fast) 40L else 450L
        val rep = object : Runnable {
            override fun run() {
                fire()
                ui.postDelayed(this, period)
            }
        }
        return Button(this).apply {
            this.text = text
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        fire()
                        ui.postDelayed(rep, first)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        ui.removeCallbacks(rep)
                    }
                }
                true
            }
        }
    }

    private fun edit(hint: String, text: String, decimal: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(text)
            setSingleLine(true)
            inputType = if (decimal)
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else InputType.TYPE_CLASS_TEXT
        }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        root.addView(label("Radio Panasonic SA-AKX34", 20f, true))
        status = TextView(this)
        root.addView(status)

        // Principal
        root.addView(label("Principal", 16f, true))
        root.addView(row(
            button("Encender / apagar") { send(Codes.POWER) },
            button("Radio (FM)") { send(Codes.TUNER) }
        ))
        root.addView(row(
            holdButton("Vol  \u2212", Codes.VOL_DN),
            holdButton("Vol  +", Codes.VOL_UP)
        ))
        root.addView(row(
            button("Mute") { send(Codes.MUTE) },
            button("CD") { send(Codes.CD) },
            button("AUX") { send(Codes.AUX) }
        ))

        // Sintonia
        root.addView(label("Sintonia", 16f, true))
        freqText = TextView(this).apply {
            textSize = 40f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(freqText)
        root.addView(row(
            holdButton("Sintonia \u2212", Codes.PREV_TUNE_DN) { addFreq(-stepValue()) },
            holdButton("Sintonia +", Codes.NEXT_TUNE_UP) { addFreq(stepValue()) }
        ))
        root.addView(label("Buscar: mantene apretado (repite rapido, como el control original)"))
        root.addView(row(
            holdButton("Buscar \u2212", Codes.PREV_TUNE_DN, true) {
                status.text = "Buscando... despues corregi la frecuencia actual"
            },
            holdButton("Buscar +", Codes.NEXT_TUNE_UP, true) {
                status.text = "Buscando... despues corregi la frecuencia actual"
            }
        ))
        root.addView(label("Ir a una frecuencia (MHz)"))
        gotoInput = edit("ej. 88.7", "", true)
        root.addView(row(gotoInput, button("Ir") { goTo() }, button("Cancelar") { cancel = true }))

        root.addView(label("Frecuencia en la que esta la radio ahora (corregila si no coincide)"))
        curInput = edit("ej. 88.1", String.format(Locale.US, "%.2f", freq), true)
        root.addView(row(curInput, button("Guardar") {
            val v = curInput.text.toString().replace(',', '.').toDoubleOrNull()
            if (v == null) toast("Numero invalido") else {
                freq = v
                saveFreq()
                showFreq()
            }
        }))

        root.addView(label("Paso del dial (MHz) y pausa entre pasos (ms)"))
        stepInput = edit("0.1", prefs.getString("step", "0.1") ?: "0.1", true)
        gapInput = edit("300", prefs.getString("gap", "300") ?: "300")
        gapInput.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(row(stepInput, gapInput))
        root.addView(button("Guardar ajustes") {
            prefs.edit().putString("step", stepInput.text.toString())
                .putString("gap", gapInput.text.toString()).apply()
            showFreq()
            toast("Guardado")
        })

        // Laboratorio
        root.addView(label("Laboratorio de codigos", 16f, true))
        root.addView(TextView(this).apply {
            text = "Sirve para encontrar el boton TUNE MODE u otros que no estan en la lista. " +
                "El escaneo prueba muchos codigos seguidos y puede activar otras funciones " +
                "(abrir bandeja, cambiar fuente, etc.). Mira el display del equipo y toca " +
                "Parar cuando algo cambie: el ultimo codigo probado queda escrito abajo."
            textSize = 13f
        })
        labGenre = edit("genero hex", "AC")
        labId = edit("id 0-3", "0")
        row(labGenre, labId).also { root.addView(it) }
        labFrom = edit("desde hex", "000")
        labTo = edit("hasta hex", "3FF")
        root.addView(row(labFrom, labTo))
        labDelay = edit("ms entre codigos", "500")
        labDelay.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(labDelay)
        labNibble = CheckBox(this).apply {
            text = "Solo comandos terminados en 0 o 1 (los habituales, mucho mas rapido)"
            isChecked = true
        }
        root.addView(labNibble)
        root.addView(button("Mantener 3 segundos (rafaga del 'desde')") { labHold() })
        root.addView(row(
            button("Enviar uno (el 'desde')") { labSendOne() },
            button("Escanear") { labScan() },
            button("Parar") { cancel = true }
        ))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    // ---------------------------------------------------------------- Frecuencia

    private fun stepValue(): Double =
        stepInput.text.toString().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 0.1

    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0

    private fun addFreq(d: Double) {
        freq = round2(freq + d)
        showFreq()
        saveFreq()
    }

    private fun saveFreq() = prefs.edit().putFloat("freq", freq.toFloat()).apply()

    private fun showFreq() {
        val fmt = if (stepValue() < 0.1) "%.2f" else "%.1f"
        freqText.text = String.format(Locale.US, "$fmt MHz", freq)
    }

    private fun goTo() {
        if (busy) { toast("Hay una operacion en curso"); return }
        val target = gotoInput.text.toString().replace(',', '.').toDoubleOrNull()
        if (target == null || target < 87.5 || target > 108.0) {
            toast("Frecuencia entre 87.5 y 108.0")
            return
        }
        val step = stepValue()
        val gap = gapInput.text.toString().toLongOrNull()?.coerceAtLeast(120) ?: 300L
        val n = ((target - freq) / step).roundToInt()
        if (n == 0) { toast("Ya esta en esa frecuencia"); return }
        val code = if (n > 0) Codes.NEXT_TUNE_UP else Codes.PREV_TUNE_DN
        val dir = if (n > 0) step else -step

        busy = true
        cancel = false
        Thread {
            var i = 0
            while (i < abs(n) && !cancel) {
                send(code)
                i++
                runOnUiThread {
                    addFreq(dir)
                    status.text = "Sintonizando... $i / ${abs(n)}"
                }
                try { Thread.sleep(gap) } catch (e: InterruptedException) { break }
            }
            busy = false
            runOnUiThread { status.text = if (cancel) "Cancelado" else "Listo" }
        }.start()
    }

    // ---------------------------------------------------------------- Laboratorio

    private fun labSendOne() {
        val g = labGenre.text.toString().toIntOrNull(16)
        val id = labId.text.toString().toIntOrNull()
        val c = labFrom.text.toString().toIntOrNull(16)
        if (g == null || id == null || c == null) { toast("Datos invalidos"); return }
        send(g, id, c)
        status.text = String.format("Enviado: genero %02X, id %d, cmd 0x%03X", g, id, c)
    }

    private fun labHold() {
        if (busy) { toast("Hay una operacion en curso"); return }
        val g = labGenre.text.toString().toIntOrNull(16)
        val id = labId.text.toString().toIntOrNull()
        val c = labFrom.text.toString().toIntOrNull(16)
        if (g == null || id == null || c == null) { toast("Datos invalidos"); return }
        busy = true
        cancel = false
        Thread {
            val end = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < end && !cancel) {
                send(g, id, c)
                try { Thread.sleep(40) } catch (e: InterruptedException) { break }
            }
            busy = false
            val txt = String.format("Rafaga enviada: genero %02X, id %d, cmd 0x%03X", g, id, c)
            runOnUiThread { status.text = txt }
        }.start()
    }

    private fun labScan() {
        if (busy) { toast("Hay una operacion en curso"); return }
        val g = labGenre.text.toString().toIntOrNull(16)
        val id = labId.text.toString().toIntOrNull()
        val from = labFrom.text.toString().toIntOrNull(16)
        val to = labTo.text.toString().toIntOrNull(16)
        val delay = labDelay.text.toString().toLongOrNull()?.coerceAtLeast(200) ?: 500L
        if (g == null || id == null || from == null || to == null) { toast("Datos invalidos"); return }
        val nibble = labNibble.isChecked

        busy = true
        cancel = false
        Thread {
            var c = from
            while (c <= to.coerceAtMost(0x3FF) && !cancel) {
                val skip = (nibble && (c and 0xE) != 0) ||
                    (g == Codes.POWER.genre && id == Codes.POWER.id && c == Codes.POWER.cmd)
                if (!skip) {
                    send(g, id, c)
                    val txt = String.format("Probando: genero %02X, id %d, cmd 0x%03X", g, id, c)
                    runOnUiThread { status.text = txt }
                    try { Thread.sleep(delay) } catch (e: InterruptedException) { break }
                }
                c++
            }
            busy = false
            runOnUiThread {
                status.text = status.text.toString().replace("Probando", if (cancel) "Parado en" else "Fin. Ultimo")
            }
        }.start()
    }

    override fun onPause() {
        cancel = true
        super.onPause()
    }
}
