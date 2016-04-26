package pw.haze.command.util

/**
 * |> Author: haze
 * |> Since: 4/25/16
 */
annotation class DigitClamp(val required: Boolean = false, val min: Double = 0.toDouble(), val max: Double = 10.toDouble())