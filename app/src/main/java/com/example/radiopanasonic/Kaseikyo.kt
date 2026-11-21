package com.example.radiopanasonic

/** Protocolo Kaseikyo (Panasonic): portadora ~37 kHz, trama de 48 bits, unidad de 432 us. */
object Kaseikyo {
    const val CARRIER = 37000
    private const val T = 432
    private const val VENDOR = 0x2002 // Panasonic

    /**
     * @param genre byte "genero" (ej. 0xAC): nibble alto = genre1, bajo = genre2
     * @param id    0..3
     * @param cmd   comando de 10 bits
     */
    fun frame(genre: Int, id: Int, cmd: Int): IntArray {
        val g1 = (genre shr 4) and 0xF
        val g2 = genre and 0xF
        var p = (VENDOR and 0xFF) xor (VENDOR shr 8)
        p = (p and 0xF) xor (p shr 4)

        val b = IntArray(6)
        b[0] = VENDOR and 0xFF
        b[1] = (VENDOR shr 8) and 0xFF
        b[2] = (g1 shl 4) or p
        b[3] = g2 or ((cmd and 0xF) shl 4)
        b[4] = ((cmd shr 4) and 0x3F) or ((id and 3) shl 6)
        b[5] = b[2] xor b[3] xor b[4]

        val out = ArrayList<Int>(99)
        out.add(8 * T)
        out.add(4 * T)
        for (byte in b) {
            for (bit in 0 until 8) { // LSB primero
                out.add(T)
                out.add(if ((byte shr bit) and 1 == 1) 3 * T else T)
            }
        }
        out.add(T)
        return out.toIntArray()
    }
}

data class Code(val genre: Int, val id: Int, val cmd: Int)

/** Codigos tomados de controles Panasonic de la misma familia (Flipper-IRDB). */
object Codes {
    val POWER = Code(0xAC, 0, 0x3D1)
    val VOL_UP = Code(0xA0, 0, 0x200)
    val VOL_DN = Code(0xA0, 0, 0x210)
    val MUTE = Code(0xA0, 0, 0x320)
    val TUNER = Code(0xA4, 2, 0x240)
    val NEXT_TUNE_UP = Code(0xAC, 1, 0x0A1)
    val PREV_TUNE_DN = Code(0xAC, 1, 0x091)
    val CD = Code(0xA0, 2, 0x140)
    val AUX = Code(0xA0, 2, 0x1A0)
}
