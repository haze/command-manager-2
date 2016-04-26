package pw.haze.command.util

import pw.haze.command.parse.isOptional
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * |> Author: haze
 * |> Since: 4/24/16
 * |> Description: My utility functions
 */

fun List<KParameter>.toKTypeArray(): Array<KType> = this.map { e -> e.type }.toTypedArray()

fun isKotlinFunction(func: KFunction<*>): Boolean = func.parameters[0].kind == KParameter.Kind.INSTANCE
fun compileKotlinFunction(func: KFunction<*>): Array<KType> = func.parameters.drop(1).toKTypeArray()
fun KFunction<*>.hasOptionals(): Boolean = optionalCount() > 0
fun KFunction<*>.optionalCount(): Int = this.parameters.filter { p -> p.type.isOptional() }.count()
fun String.sentenceForm(): String = this[0].toUpperCase() + this.substring(1).toLowerCase()
fun <T> List<T>.findFirst(predicate: (T) -> Boolean): Optional<T> {
    items@ for (item: T in this) {
        if (predicate(item)) return Optional.of(item)
    }
    return Optional.empty()
}