package com.irisx.ai.core.tools

import android.content.Context
import com.irisx.ai.core.agent.IrisTool
import com.irisx.ai.core.agent.ToolResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Tiny fully offline recursive-descent expression evaluator.
 * Supports + - * / % ^ and parentheses, plus spoken words like "plus" and "into".
 */
object Calc {

    fun eval(input: String): Double? {
        val cleaned = input.lowercase(Locale.US)
            .replace("multiplied by", "*")
            .replace("divided by", "/")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("into", "*")
            .replace("x", "*")
            .replace("\u00f7", "/")
            .replace("\u00d7", "*")
            .replace(",", "")
            .filter { it.isDigit() || it in "+-*/%^()." }
        if (cleaned.isBlank()) return null
        return runCatching { Parser(cleaned).parse() }.getOrNull()
    }

    fun pretty(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "invalid"
        if (abs(d - d.roundToLong()) < 0.000000001) return d.roundToLong().toString()
        return String.format(Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            if (i < s.length) throw IllegalArgumentException("unexpected input")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i]
                i++
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i]
                i++
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') {
                i++
                return -unary()
            }
            if (i < s.length && s[i] == '+') {
                i++
                return unary()
            }
            return atom()
        }

        private fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

class CalculatorTool : IrisTool {
    override val name = "calculator"
    override val description = "Do arithmetic and percentage math completely offline"
    override val params = mapOf("expression" to "Math expression, for example 350*18/100")

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val raw = (args["expression"] ?: args["query"] ?: "").trim()
        if (raw.isEmpty()) return ToolResult(false, "Kya calculate karna hai?")
        val value = Calc.eval(raw) ?: return ToolResult(false, "Ye calculation samajh nahi aaya")
        return ToolResult(true, "Jawab hai " + Calc.pretty(value))
    }
}

class UnitConvertTool : IrisTool {
    override val name = "unit_convert"
    override val description =
        "Convert length, weight, volume, speed, data size and temperature units offline"
    override val params = mapOf(
        "value" to "Numeric amount to convert",
        "from" to "Source unit, for example km",
        "to" to "Target unit, for example mile"
    )

    private val factors: Map<String, Pair<String, Double>> = mapOf(
        "mm" to Pair("length", 0.001),
        "cm" to Pair("length", 0.01),
        "m" to Pair("length", 1.0),
        "meter" to Pair("length", 1.0),
        "metre" to Pair("length", 1.0),
        "km" to Pair("length", 1000.0),
        "kilometer" to Pair("length", 1000.0),
        "inch" to Pair("length", 0.0254),
        "in" to Pair("length", 0.0254),
        "foot" to Pair("length", 0.3048),
        "feet" to Pair("length", 0.3048),
        "ft" to Pair("length", 0.3048),
        "yard" to Pair("length", 0.9144),
        "mile" to Pair("length", 1609.344),
        "mg" to Pair("mass", 0.000001),
        "g" to Pair("mass", 0.001),
        "gram" to Pair("mass", 0.001),
        "kg" to Pair("mass", 1.0),
        "kilogram" to Pair("mass", 1.0),
        "ton" to Pair("mass", 1000.0),
        "pound" to Pair("mass", 0.45359237),
        "lb" to Pair("mass", 0.45359237),
        "ounce" to Pair("mass", 0.0283495),
        "oz" to Pair("mass", 0.0283495),
        "ml" to Pair("volume", 0.001),
        "l" to Pair("volume", 1.0),
        "litre" to Pair("volume", 1.0),
        "liter" to Pair("volume", 1.0),
        "gallon" to Pair("volume", 3.785412),
        "kb" to Pair("data", 1.0),
        "mb" to Pair("data", 1024.0),
        "gb" to Pair("data", 1048576.0),
        "tb" to Pair("data", 1073741824.0),
        "kmph" to Pair("speed", 1.0),
        "kph" to Pair("speed", 1.0),
        "mph" to Pair("speed", 1.609344),
        "mps" to Pair("speed", 3.6)
    )

    private val tempUnits: Map<String, String> = mapOf(
        "c" to "c",
        "celsius" to "c",
        "centigrade" to "c",
        "f" to "f",
        "fahrenheit" to "f",
        "k" to "k",
        "kelvin" to "k"
    )

    private fun norm(unit: String): String {
        val key = unit.lowercase(Locale.US).trim().removeSuffix(".").removeSuffix("\u00b0")
        if (factors.containsKey(key) || tempUnits.containsKey(key)) return key
        val singular = key.removeSuffix("s")
        return singular
    }

    override fun run(context: Context, args: Map<String, String>): ToolResult {
        val value = args["value"]?.trim()?.toDoubleOrNull()
            ?: return ToolResult(false, "Kitna convert karna hai?")
        val from = norm(args["from"].orEmpty())
        val to = norm(args["to"].orEmpty())
        if (from.isEmpty() || to.isEmpty()) return ToolResult(false, "Unit clear nahi hai")

        val fromTemp = tempUnits[from]
        val toTemp = tempUnits[to]
        if (fromTemp != null && toTemp != null) {
            val celsius = when (fromTemp) {
                "f" -> (value - 32.0) * 5.0 / 9.0
                "k" -> value - 273.15
                else -> value
            }
            val out = when (toTemp) {
                "f" -> celsius * 9.0 / 5.0 + 32.0
                "k" -> celsius + 273.15
                else -> celsius
            }
            return ToolResult(
                true,
                Calc.pretty(value) + " degree " + fromTemp.uppercase(Locale.US) + " = " +
                    Calc.pretty(out) + " degree " + toTemp.uppercase(Locale.US)
            )
        }

        val src = factors[from] ?: return ToolResult(false, from + " unit samajh nahi aayi")
        val dst = factors[to] ?: return ToolResult(false, to + " unit samajh nahi aayi")
        if (src.first != dst.first) {
            return ToolResult(false, from + " ko " + to + " me convert nahi kar sakta")
        }
        val out = value * src.second / dst.second
        return ToolResult(true, Calc.pretty(value) + " " + from + " = " + Calc.pretty(out) + " " + to)
    }
}
