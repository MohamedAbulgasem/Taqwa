package world.taqwa.app.wear.link

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.net.JarURLConnection

/** One method a compiled class refers to: its owner's internal name (`a/b/C`) and the method's name. */
internal data class MethodReference(val owner: String, val name: String)

/**
 * What the watch app's compiled classes refer to, read from their constant pools (JVMS 4.4), for
 * the rules no unit test can watch Play services keep (Ruling 54 item 1, on the watch as on the
 * phone, whose `:shared` host tests hold the same file): which of its classes the watch's link
 * asks, and that it asks none of the calls that show something. A method the code calls, or hands
 * on as a reference, is a `Methodref` or an `InterfaceMethodref` there, whatever the source looked
 * like and whatever its comments say.
 */
internal object ClassReferences {

    /** Every method referred to by the classes of [type]'s package, as compiled beside it. */
    fun inPackageOf(type: Class<*>): Set<MethodReference> = classFilesBeside(type).flatMap(::inClass).toSet()

    /** The class files of [type]'s package, from the directory or the jar [type] was loaded from. */
    private fun classFilesBeside(type: Class<*>): List<ByteArray> {
        val url = checkNotNull(type.getResource("${type.simpleName}.class")) { "no class file for ${type.name}" }
        val directory = type.name.substringBeforeLast('.').replace('.', '/') + "/"
        return when (url.protocol) {
            "file" -> checkNotNull(File(url.toURI()).parentFile).listFiles { file -> file.name.endsWith(".class") }.orEmpty().map { it.readBytes() }
            "jar" -> {
                val connection = url.openConnection() as JarURLConnection
                connection.useCaches = false // a jar of its own, so closing it leaves the class loader's open
                connection.jarFile.use { jar ->
                    jar.entries().toList()
                        .filter { it.name.startsWith(directory) && it.name.endsWith(".class") && '/' !in it.name.removePrefix(directory) }
                        .map { entry -> jar.getInputStream(entry).use { it.readBytes() } }
                }
            }
            else -> error("classes loaded from $url")
        }
    }

    /** The methods one class file refers to. */
    private fun inClass(bytes: ByteArray): List<MethodReference> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        check(input.readInt() == 0xCAFEBABE.toInt()) { "not a class file" }
        input.readUnsignedShort() // minor version
        input.readUnsignedShort() // major version
        val count = input.readUnsignedShort()
        val texts = arrayOfNulls<String>(count)
        val classNames = IntArray(count)
        val methodNames = IntArray(count)
        val methods = mutableListOf<Pair<Int, Int>>()
        var index = 1
        while (index < count) {
            when (val tag = input.readUnsignedByte()) {
                UTF8 -> texts[index] = input.readUTF() // a u2 length, then modified UTF-8, as readUTF reads it
                CLASS -> classNames[index] = input.readUnsignedShort()
                METHOD_REF, INTERFACE_METHOD_REF -> methods += input.readUnsignedShort() to input.readUnsignedShort()
                NAME_AND_TYPE -> {
                    methodNames[index] = input.readUnsignedShort()
                    input.readUnsignedShort()
                }
                LONG, DOUBLE -> {
                    input.skipBytes(8)
                    index++ // eight-byte constants take two entries
                }
                INTEGER, FLOAT, FIELD_REF, DYNAMIC, INVOKE_DYNAMIC -> input.skipBytes(4)
                METHOD_HANDLE -> input.skipBytes(3)
                STRING, METHOD_TYPE, MODULE, PACKAGE -> input.skipBytes(2)
                else -> error("constant pool tag $tag")
            }
            index++
        }
        return methods.map { (owner, nameAndType) ->
            MethodReference(checkNotNull(texts[classNames[owner]]), checkNotNull(texts[methodNames[nameAndType]]))
        }
    }

    private const val UTF8 = 1
    private const val INTEGER = 3
    private const val FLOAT = 4
    private const val LONG = 5
    private const val DOUBLE = 6
    private const val CLASS = 7
    private const val STRING = 8
    private const val FIELD_REF = 9
    private const val METHOD_REF = 10
    private const val INTERFACE_METHOD_REF = 11
    private const val NAME_AND_TYPE = 12
    private const val METHOD_HANDLE = 15
    private const val METHOD_TYPE = 16
    private const val DYNAMIC = 17
    private const val INVOKE_DYNAMIC = 18
    private const val MODULE = 19
    private const val PACKAGE = 20
}
