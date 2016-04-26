package pw.haze.command.parse

import pw.haze.command.InvalidParametersException
import pw.haze.command.util.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.defaultType
import kotlin.reflect.jvm.javaType


/**
 * |> Author: haze
 * |> Since: 4/23/16
 * |> Description: Takes strings, spits out an array, Pair<String, ObjectType>
 */

class StringClampException(val clamp: StringClamp, val str: String) : Exception() {
    override val message: String?
        get() =
        if (str.length > clamp.max)
            "\"$str\"'s length exceeded the maximum amount of characters! [$clamp.max]"
        else
            "\"$str\"'s length did not meet the minimum amount of characters! [$clamp.min]"
}

class DigitClampException(val clamp: DigitClamp, val num: Number) : Exception() {
    override val message: String?
        get() =
        if (num is Int) {
            if (num.toInt() > clamp.max)
                "\"$num\" is bigger than the clamps max! [$clamp.max]"
            else
                "\"$num\" is smaller than the clamps minimum! [$clamp.min]"
        } else {
            if (num.toDouble() > clamp.max)
                "\"$num\" is bigger than the clamps max! [$clamp.max]"
            else
                "\"$num\" is smaller than the clamps minimum! [$clamp.min]"
        }
}

enum class ArgumentType() {
    BOOLEAN, DOUBLE, INT, STRING, OPTIONAL
}

fun KType.argtype(): ArgumentType {
    val shrtType = toString().split(".")[toString().split(".").size - 1]
    return when (shrtType.toLowerCase()) {
        "double" -> ArgumentType.DOUBLE
        "boolean" -> ArgumentType.BOOLEAN
        "int", "integer" -> ArgumentType.INT
        "string" -> ArgumentType.STRING
        else -> ArgumentType.OPTIONAL
    }
}

fun String.type(): ArgumentType {
    if (isBoolean(this)) {
        return ArgumentType.BOOLEAN
    } else if (isDouble(this)) {
        return ArgumentType.DOUBLE
    } else if (isInteger(this)) {
        return ArgumentType.INT
    }
    return ArgumentType.STRING
}

// -- Super Fucking Hacky way of hacking type erasure of parameters to get supertype of optionals
fun KType.superType(): ArgumentType {
    val type = this.toString().split("<")[1].replace(">", "")
    return when (type.toLowerCase().split(".")[type.split(".").size - 1]) {
        "double" -> ArgumentType.DOUBLE
        "boolean" -> ArgumentType.BOOLEAN
        "int", "integer" -> ArgumentType.INT
        "string" -> ArgumentType.STRING
        else -> ArgumentType.OPTIONAL
    }
}

fun KType.isOptional(): Boolean {
    val temp = this.toString().split("<")[0]
    return temp.split(".")[temp.split(".").size - 1].toLowerCase().equals("optional")
}

fun parseArguments(str: String, catalyst: String, method: KFunction<*>): Pair<List<Pair<String, Any>>, String> {

    fun List<KParameter>.withoutOptionals(): List<KParameter> = this.filter { p -> !p.type.isOptional() }

    fun strEscape(str: String): String = if (str.startsWith("str_")) str.substring(4) else str

    fun strClamp(str: String, cmp: StringClamp): String {
        if ((str.length > cmp.max || str.length < cmp.min) && cmp.required) throw StringClampException(cmp, str)
        if (str.length > cmp.max) {
            return str.removeRange(cmp.max, str.length)
        }
        return str
    }

    fun doubleClamp(num: Double, cmp: DigitClamp): Double {
        var nnum = num
        if ((nnum > cmp.max || nnum < cmp.min) && cmp.required) throw DigitClampException(cmp, num)
        if (nnum < cmp.min)
            nnum = cmp.min
        else if (nnum > cmp.max)
            nnum = cmp.max
        return nnum
    }

    fun intClamp(num: Int, cmp: DigitClamp): Int {
        var nnum = num
        val max = Math.round(cmp.max).toInt()
        val min = Math.round(cmp.min).toInt()
        if ((nnum > max || nnum < min) && cmp.required) throw DigitClampException(cmp, num)
        if (nnum < min)
            nnum = min
        else if (nnum > max)
            nnum = max
        return nnum
    }

    fun arguments(str: String): List<String> {
        val pattern_str = "[^\\s\"']+|\"([^\"]*)\"|'([^']*)'"
        val pattern = Pattern.compile(pattern_str)
        val matcher: Matcher = pattern.matcher(str)
        val list: MutableList<String> = arrayListOf()

        fun occurrences(matcher: Matcher): Int {
            var hits = 0
            hits@ while (matcher.find())
                hits++
            return hits
        }

        when (occurrences(pattern.matcher(str))) {
            1 -> list.add(str.substring(catalyst.length))
            else -> {
                while (matcher.find()) {
                    var group = matcher.group()
                    if (group.contains("\""))
                        group = "str_${group.replace("\"", "")}"
                    list.add(group)
                }
            }
        }
        return list
    }

    fun generateArgumentTypes(args: List<String>): List<Pair<String, ArgumentType>> = args.map { arg -> Pair(arg, arg.type()) }

    val arguments = arguments(str).drop(1)
    val pairs: MutableList<Pair<String, Any>> = mutableListOf()
    var params = method.parameters
    if (isKotlinFunction(method)) params = params.drop(1) // dropping instance
    if (method.hasOptionals()) {
        if (params.count() != arguments.size && params.withoutOptionals().count() != arguments.size) {
            val requestedStr = generateArgumentTypes(arguments)
            return Pair(listOf(), "[a|m]: *contains optionals* ${requestedStr.map { c -> Pair(strEscape(c.first), c.second.name.sentenceForm()) }}")
        } else {
            params@ for ((i, param) in params.withIndex()) {
                val annotations = param.annotations
                val digitClamp: DigitClamp? = annotations.find { a -> a is DigitClamp } as DigitClamp?
                val stringclamp: StringClamp? = annotations.find { a -> a is StringClamp } as StringClamp?
                if (i >= arguments.size && param.type.isOptional()) {
                    pairs.add(Pair("Optional<>", Optional.empty<Any>()))
                } else {
                    val argument = arguments[i]
                    if (param.type.argtype() == ArgumentType.OPTIONAL) {
                        val superType = param.type.superType()

                        if (isBoolean(argument) && superType == ArgumentType.BOOLEAN) {
                            pairs.add(Pair("$argument Optional<Boolean>", Optional.of(argument.toBoolean())))
                        } else if (isDouble(argument) && !superType.equals(Int::class.defaultType) && superType == ArgumentType.DOUBLE) {
                            var digit = argument.toDouble()
                            digit = if (digitClamp != null) doubleClamp(digit, digitClamp) else digit

                            pairs.add(Pair("$argument Optional<Double>", Optional.of(digit)))
                        } else if (isInteger(argument) && superType == ArgumentType.INT) {
                            var digit = argument.toInt()
                            digit = if (digitClamp != null) intClamp(digit, digitClamp) else digit
                            pairs.add(Pair("$argument Optional<Int>", Optional.of(digit)))
                        } else if (superType == ArgumentType.STRING) {
                            var str = argument
                            if (argument.startsWith("str_"))
                                str = strEscape(argument)
                            str = if (stringclamp != null) strClamp(str, stringclamp) else str
                            pairs.add(Pair("$argument Optional<String>", Optional.of(str)))
                        } else {
                            throw InvalidParametersException(argument, param.type.javaType.typeName)
                        }
                    } else {
                        if (isBoolean(argument)) {
                            pairs.add(Pair("$argument [Boolean]", argument.toBoolean()))
                        } else if (isDouble(argument) && !param.type.equals(Int::class.defaultType)) {
                            pairs.add(Pair("$argument [Double]", argument.toDouble()))
                        } else if (isInteger(argument)) {
                            pairs.add(Pair("$argument [Int]", argument.toInt()))
                        } else {
                            var str = argument
                            if (argument.startsWith("str_"))
                                str = strEscape(argument)
                            str = if (stringclamp != null) strClamp(str, stringclamp) else str
                            pairs.add(Pair("$argument [String]", str))
                        }
                    }
                }
            }
            return Pair(pairs, "Success")
        }
    } else {
        println("p= $params c=${params.size}")
        println("a= $arguments c=${arguments.size}")
        if (params.count() != arguments.size) {
            val requestedStr = generateArgumentTypes(arguments)
            return Pair(listOf(), "[a|m]: ${requestedStr.map { c -> Pair(c.first, c.second.name.sentenceForm()) }}")
        }
        args@ for ((i, argument) in arguments.withIndex()) {
            val paramType = params[i].type
            if (isBoolean(argument)) {
                pairs.add(Pair("$argument [Boolean]", argument.toBoolean()))
            } else if (isDouble(argument) && !paramType.equals(Int::class.defaultType)) {
                pairs.add(Pair("$argument [Double]", argument.toDouble()))
            } else if (isInteger(argument)) {
                pairs.add(Pair("$argument [Int]", argument.toInt()))
            } else {
                pairs.add(Pair("$argument [String]", argument))
            }
        }
        return Pair(pairs, "Success")
    }
}


// --  Thanks to: Jonas Klemming
fun isInteger(str: String): Boolean {
    if (str.length == 0 || str.isNullOrEmpty()) return false
    var i: Int = 0
    if (str[0] == '-') {
        if (str.length == 1) return false
        i = 1
    }
    ints@ while (i < str.length) {
        val char = str[i]
        if (char < '0' || char > '9') return false
        i++
    }
    return true
}

// -- Java Compliant Double check.
fun isDouble(str: String): Boolean {
    if (str.length == 0 || str.isNullOrEmpty()) return false
    var pass = true
    var hasNegative = false
    chars@ for (char in str) {
        if (char == '.') continue
        if (!hasNegative && char == '-' && str.indexOf(char) == 0) {
            hasNegative = true
            continue
        } else if ((hasNegative && char == '-') || (char == '-' && str.indexOf(char) != 0)) {
            return false // Multiple negative signs? naaa
        }
        pass = pass && Character.isDigit(char)
    }
    return pass
}


// -- Simple Check
fun isBoolean(str: String): Boolean = str.equals("true", true) || str.equals("false", true)