package com.mauro.wgbg

/**
 * A small JSON reader for data files the app ships (critter specs): objects become ordered maps, arrays lists, numbers
 * doubles. No Android classes, so unit tests read the same files the app does.
 */
internal object MiniJson {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); ws(); require(at == text.length) { "JSON: trailing text at $at" }; v }

    private class Reader(val s: String) {
        var at = 0

        fun ws() { while (at < s.length && s[at].isWhitespace()) at++ }

        fun value(): Any? {
            ws()
            require(at < s.length) { "JSON: unexpected end" }
            return when (s[at]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> num()
            }
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            at++; ws()
            if (s[at] == '}') { at++; return m }
            while (true) {
                ws(); val k = str(); ws()
                require(s[at] == ':') { "JSON: ':' expected at $at" }; at++
                m[k] = value(); ws()
                when (s[at++]) { ',' -> continue; '}' -> return m; else -> error("JSON: ',' or '}' expected at ${at - 1}") }
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            at++; ws()
            if (s[at] == ']') { at++; return l }
            while (true) {
                l.add(value()); ws()
                when (s[at++]) { ',' -> continue; ']' -> return l; else -> error("JSON: ',' or ']' expected at ${at - 1}") }
            }
        }

        fun str(): String {
            require(s[at] == '"') { "JSON: string expected at $at" }
            val b = StringBuilder(); at++
            while (true) {
                val c = s[at++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[at++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                        'u' -> { b.append(s.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
        }

        fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, at)) { "JSON: bad literal at $at" }
            at += w.length; return v
        }

        fun num(): Double {
            val start = at
            while (at < s.length && (s[at].isDigit() || s[at] in "+-.eE")) at++
            return s.substring(start, at).toDouble()
        }
    }
}
