package pw.haze.command

import pw.haze.command.parse.isOptional
import pw.haze.command.parse.parseArguments
import pw.haze.command.parse.superType
import pw.haze.command.parse.type
import pw.haze.command.util.isKotlinFunction
import pw.haze.command.util.sentenceForm
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.declaredFunctions
import kotlin.reflect.defaultType

/**
 * |> Author: haze
 * |> Since: 4/23/16
 */

class InvalidInstanceException(val e_message: String) : Exception(e_message)

class InvalidParametersException(val provided: String, val required: String) : Exception() {
    override val message: String?
        get() = "[IVP]: Invalid Parameters! Was supplied \"$provided\" [${provided.javaClass.simpleName}]{${provided.type()}}, expected $required"
}

class CommandManager(val catalyst: String) {
    private val commands: LinkedHashMap<KClass<*>, ArrayList<KFunction<*>>> = LinkedHashMap()
    private val instances: LinkedHashMap<KClass<*>, Any> = LinkedHashMap()

    constructor() : this(".")

    fun KClass<*>.getFunctionsWithAnnotation(anno: KClass<out Annotation>): List<KFunction<*>> =
            this.declaredFunctions.filter { func -> func.isAnnotationPresent(anno) }

    fun KFunction<*>.isAnnotationPresent(anno: KClass<out Annotation>): Boolean = this.annotations.any { a -> a.annotationClass.equals(anno) }

    fun KFunction<*>.getAnnotation(anno: KClass<out Annotation>): Annotation? {
        if (isAnnotationPresent(anno)) {
            annotations@ for (an: Annotation in this.annotations)
                if (an.annotationClass.equals(anno))
                    return an
        }
        return null
    }


    fun register(obj: Any) {
        val clazz = obj.javaClass.kotlin
        if (!this.instances.containsKey(clazz))
            this.instances.put(clazz, obj)
        this.commands.put(clazz, clazz.getFunctionsWithAnnotation(Command::class) as ArrayList<KFunction<*>>)

    }

    private fun usage(func: KFunction<*>): String {
        val builder = StringBuilder()
        var params = func.parameters
        if (isKotlinFunction(func)) params = params.drop(1)
        params.forEach { p ->
            val typeStr = p.type.toString()
            if (p.type.isOptional())
                builder.append("Optional<${p.type.superType().name.sentenceForm()}>, ")
            else
                builder.append("${typeStr.split(".")[typeStr.split(".").size - 1]}, ")
        }
        builder.replace(builder.length - 2, builder.length, ".")
        return builder.toString()
    }

    fun execute(input: String): String {
        try {
            if (input.startsWith(catalyst, true)) {
                val command = input.split(" ")[0].substring(catalyst.length)
                val commandPair = pairFromName(command)
                if (commandPair.first.isPresent) {
                    val func = commandPair.first.get().second
                    val argPair = parseArguments(input, catalyst, func)
                    if (!argPair.second.equals("Success")) return "Argument Mismatch: Required = \"${usage(func)}\", received = \"${argPair.second.split(":")[1].trim()}\"    "
                    val ret = invoke(commandPair.first.get(), argPair.first)
                    return if (!ret.trim().isNullOrBlank()) ret else ""
                } else
                    return commandPair.second
            }
        } catch(e: Exception) {
            return when (e) {
                is InvalidParametersException -> {
                    val ivp = e
                    ivp.message ?: "InvalidParamException is null! provided=${ivp.provided} requested=${ivp.required}"
                }
                else -> {
                    e.message ?: "Exception Caught: Null Message!"
                }
            }
        }
        return "Supplied command does not start with catalyst, \"$catalyst\"."
    }

    private fun findCommandClass(func: KFunction<*>): Optional<KClass<*>> {
        insts@ for (entry in commands) {
            funcs@ for (func_n in entry.value) {
                if (func_n == func) return Optional.of(entry.key)
            }
        }
        return Optional.empty()
    }

    private fun pairFromName(command: String): Pair<Optional<Pair<KClass<*>, KFunction<*>>>, String> {
        val func = methodFromName(command)
        if (func.isPresent) {
            val clazz = findCommandClass(func.get())
            if (clazz.isPresent)
                return Pair(Optional.of(Pair(clazz.get(), func.get())), "Success")
            else
                return Pair(Optional.empty(), "Class for function ${func.get()} [$command] not found.")
        } else {
            return Pair(Optional.empty(), "Function for command $command not found.")
        }
    }

    fun methodFromName(alias: String): Optional<KFunction<*>> {

        fun Array<String>.containsIgnoreCase(look: String): Boolean {
            strings@ for (str in this)
                if (str.equals(look, true)) return true
            return false
        }

        arrays@ for (array in commands.values)
            functions@ for (function in array)
                if (function.isAnnotationPresent(Command::class)) {
                    val commandAnnotation = function.getAnnotation(Command::class) as Command
                    if (commandAnnotation.value.containsIgnoreCase(alias)) return Optional.of(function)
                }
        return Optional.empty()
    }

    private fun invoke(methodData: Pair<KClass<*>, KFunction<*>>, args: List<Pair<String, Any>>): String {
        fun compile(args: List<Pair<String, Any>>): List<Any> = args.toMap().values.toList()

        fun instFromClass(clazz: KClass<*>): Any {
            val inst: Map.Entry<KClass<*>, Any>? = this.instances.asSequence().find { e -> e.key == clazz }
            if (inst == null) throw InvalidInstanceException("Class ${clazz.simpleName} has no found instance!")
            else return inst.value
        }

        val method = methodData.second
        val clazz = methodData.first
        val inst = instFromClass(clazz)
        val compiledArgs = compile(args)

        if (method.returnType == String.javaClass.kotlin.defaultType)
            return method.call(inst, *compiledArgs.toTypedArray()) as String
        else
            method.call(inst, *compiledArgs.toTypedArray())
        return ""
    }

}