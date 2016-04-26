package pw.haze.command.test

import pw.haze.command.Command
import pw.haze.command.CommandManager
import java.util.*

/**
 * |> Author: haze
 * |> Since: 4/24/16
 */

class Test {
    @Command(arrayOf("test")) fun doTest(testOne: Int, testTwo: Optional<Int>) {
        if (testTwo.isPresent)
            println("found testTwo, $testOne + $testTwo")
        else
            println("found no testTwo, $testOne")
    }
}


fun main(args: Array<String>) {
    val cmd = CommandManager()
    cmd.register(Test())
    println(cmd.execute(".test 5"))
}