package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// A small evaluator for the expressions Trail Map's v1 and v2 styles use in their image references, and the night
// switch's forms of them, so NightImageSwitchTest can ask what a reference draws for a given feature and zoom rather
// than how its JSON is spelt. Only these operators: get, zoom, ==, in, literal, length, concat, match, case, step.
// Anything else fails the test loudly; it is never guessed at. Not MapLibre's parser: the zoom rule is checked
// separately (zoomRuleViolations).

/** What [expression] gives for a feature with [properties] at [zoom]: a string, number, boolean, list, or null. */
internal fun evaluateStyleExpression(expression: JsonElement, properties: Map<String, Any?>, zoom: Double): Any? {
    fun eval(e: JsonElement): Any? = evaluateStyleExpression(e, properties, zoom)
    return when (expression) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            expression.isString -> expression.content
            expression.booleanOrNull != null -> expression.booleanOrNull
            else -> expression.doubleOrNull
        }
        is JsonArray -> {
            val op = (expression[0] as JsonPrimitive).content
            val args = expression.drop(1)
            when (op) {
                "get" -> properties[(args[0] as JsonPrimitive).content]
                "zoom" -> zoom
                "==" -> eval(args[0]) == eval(args[1])
                "literal" -> (args[0] as JsonArray).map { (it as JsonPrimitive).content }
                "in" -> (eval(args[1]) as List<*>).contains(eval(args[0]))
                "length" -> (eval(args[0]) as? String)?.length?.toDouble()
                "concat" -> args.joinToString("") { a -> eval(a).let { if (it is Double && it % 1.0 == 0.0) it.toLong().toString() else it.toString() } }
                "match" -> {
                    val input = eval(args[0])
                    var i = 1
                    while (i + 1 < args.size) {
                        val label = args[i]
                        val labels = if (label is JsonArray) label.map { (it as JsonPrimitive).content } else listOf((label as JsonPrimitive).content)
                        if (input is String && input in labels) return eval(args[i + 1])
                        i += 2
                    }
                    eval(args.last())
                }
                "case" -> {
                    var i = 0
                    while (i + 1 < args.size) {
                        if (eval(args[i]) == true) return eval(args[i + 1])
                        i += 2
                    }
                    eval(args.last())
                }
                "step" -> {
                    val input = eval(args[0]) as Double
                    var out = args[1]
                    var i = 2
                    while (i + 1 < args.size) {
                        if (input >= ((args[i] as JsonPrimitive).doubleOrNull ?: error("step stop"))) out = args[i + 1]
                        i += 2
                    }
                    eval(out)
                }
                else -> error("the test evaluator does not read \"$op\"")
            }
        }
        else -> error("the test evaluator does not read $expression")
    }
}

private fun isZoom(e: JsonElement): Boolean = e is JsonArray && e.size == 1 && (e[0] as? JsonPrimitive)?.content == "zoom"

private fun countZoom(e: JsonElement): Int = when {
    isZoom(e) -> 1
    e is JsonArray -> e.sumOf { countZoom(it) }
    else -> 0
}

/**
 * MapLibre Native's rule for a zoom-dependent property (`findZoomCurve`, `find_zoom_curve.cpp:15-82` at
 * `android-v13.5.0`, as Sorrel's review of PR #380 reads it): `["zoom"]` may appear only as the input of a top-level
 * `step` or `interpolate`. Returns what breaks the rule in [expression], or nothing. Stricter than MapLibre in one way:
 * it does not look through `let` or `coalesce`, which no Trail Map image reference uses.
 */
internal fun zoomRuleViolations(expression: JsonElement): List<String> {
    val total = countZoom(expression)
    if (total == 0) return emptyList()
    val top = (expression as? JsonArray)?.getOrNull(0) as? JsonPrimitive
    val inputIndex = when (top?.content) {
        "step" -> 1
        "interpolate", "interpolate-hcl", "interpolate-lab" -> 2
        else -> return listOf("\"zoom\" used under a top-level \"${top?.content}\", not as a top-level step or interpolate input")
    }
    val array = expression as JsonArray
    return buildList {
        if (!isZoom(array[inputIndex])) add("\"zoom\" is not the input of the top-level ${top!!.content}")
        if (total > 1) add("\"zoom\" occurs $total times; only the top-level ${top!!.content}'s input may use it")
    }
}
