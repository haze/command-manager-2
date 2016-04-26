package pw.haze.command.util

/**
 * |> Author: haze
 * |> Since: 4/25/16
 */
annotation class StringClamp(val required: Boolean = false, val min: Int = 0, val max: Int = 256)