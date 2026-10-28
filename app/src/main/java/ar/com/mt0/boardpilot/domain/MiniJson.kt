package ar.com.mt0.boardpilot.domain

/**
 * Parser JSON minimo en Kotlin puro (objetos, arrays, strings con escapes,
 * numeros, booleanos y null) para interpretar la salida de los modelos sin
 * sumar dependencias. Devuelve Map/List/String/Double/Boolean/null y lanza
 * IllegalArgumentException ante entrada invalida.
 */
object MiniJson {
    /** Extrae el primer objeto {...} del texto (tolera prosa/cercos) o null. */
    fun parseObjectLenient(text: String): Map<String, Any?>? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed = runCatching { parse(text.substring(start, end + 1)) }
            .getOrNull() as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        return parsed as Map<String, Any?>
    }

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWs()
        require(parser.atEnd()) { "sobra contenido tras el JSON" }
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWs()
            require(i < s.length) { "fin inesperado" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (consume('}')) return out
            while (true) {
                skipWs()
                val key = string()
                skipWs()
                expect(':')
                out[key] = value()
                skipWs()
                if (consume('}')) return out
                expect(',')
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (consume(']')) return out
            while (true) {
                out += value()
                skipWs()
                if (consume(']')) return out
                expect(',')
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "string sin cerrar" }
                when (val ch = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "escape incompleto" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "unicode incompleto" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("escape invalido: $e")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
        }

        private fun number(): Double {
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            require(i > start) { "valor invalido en $start" }
            return s.substring(start, i).toDoubleOrNull()
                ?: throw IllegalArgumentException("numero invalido en $start")
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, i)) { "literal invalido en $i" }
            i += word.length
            return value
        }

        private fun expect(ch: Char) {
            skipWs()
            require(i < s.length && s[i] == ch) { "se esperaba '$ch' en $i" }
            i++
        }

        private fun consume(ch: Char): Boolean {
            skipWs()
            return if (i < s.length && s[i] == ch) {
                i++
                true
            } else {
                false
            }
        }
    }
}
