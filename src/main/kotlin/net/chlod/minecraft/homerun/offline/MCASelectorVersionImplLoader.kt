package net.chlod.minecraft.homerun.offline

import net.querz.mcaselector.version.MCVersionImplementation
import net.querz.mcaselector.version.VersionHandler
import net.querz.mcaselector.version.java_1_13.*
import net.querz.mcaselector.version.java_1_14.*
import net.querz.mcaselector.version.java_1_15.ChunkFilter_19w34a
import net.querz.mcaselector.version.java_1_15.ChunkFilter_19w36a
import net.querz.mcaselector.version.java_1_16.ChunkFilter_20w06a
import net.querz.mcaselector.version.java_1_16.ChunkFilter_20w13a
import net.querz.mcaselector.version.java_1_16.ChunkFilter_20w17a
import net.querz.mcaselector.version.java_1_17.ChunkFilter_20w45a
import net.querz.mcaselector.version.java_1_17.ChunkFilter_21w06a
import net.querz.mcaselector.version.java_1_17.ChunkFilter_21w15a
import net.querz.mcaselector.version.java_1_18.ChunkFilter_21w37a
import net.querz.mcaselector.version.java_1_18.ChunkFilter_21w43a
import net.querz.mcaselector.version.java_1_19.ChunkFilter_22w11a
import net.querz.mcaselector.version.java_1_20.ChunkFilter_23w12a
import net.querz.mcaselector.version.java_1_20.ChunkFilter_24w09a
import net.querz.mcaselector.version.java_1_20.ChunkFilter_24w10a
import net.querz.mcaselector.version.java_1_21.ChunkFilter_1_21_5_RC2
import net.querz.mcaselector.version.java_1_21.ChunkFilter_24w18a
import net.querz.mcaselector.version.java_1_21.ChunkFilter_25w02a
import net.querz.mcaselector.version.java_1_21.ChunkFilter_25w32a
import net.querz.mcaselector.version.java_1_9.ChunkFilter_15w32a
import net.querz.mcaselector.version.java_null.ChunkFilter_Null
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.util.*

class MCASelectorVersionImplLoader {

    companion object {

        var implementations: MutableMap<Class<*>?, TreeMap<Int?, Any?>> = HashMap<Class<*>?, TreeMap<Int?, Any?>>()
        val CHUNK_FILTER_CLASSES = listOf(
            ChunkFilter_1_13_PRE3::class.java,
            ChunkFilter_17w47a::class.java,
            ChunkFilter_18w06a::class.java,
            ChunkFilter_18w16a::class.java,
            ChunkFilter_18w19a::class.java,
            ChunkFilter_1_14_PRE1::class.java,
            ChunkFilter_18w43a::class.java,
            ChunkFilter_18w45a::class.java,
            ChunkFilter_19w02a::class.java,
            ChunkFilter_19w11a::class.java,
            ChunkFilter_19w34a::class.java,
            ChunkFilter_19w36a::class.java,
            ChunkFilter_20w06a::class.java,
            ChunkFilter_20w13a::class.java,
            ChunkFilter_20w17a::class.java,
            ChunkFilter_20w45a::class.java,
            ChunkFilter_21w06a::class.java,
            ChunkFilter_21w15a::class.java,
            ChunkFilter_21w37a::class.java,
            ChunkFilter_21w43a::class.java,
            ChunkFilter_22w11a::class.java,
            ChunkFilter_23w12a::class.java,
            ChunkFilter_24w09a::class.java,
            ChunkFilter_24w10a::class.java,
            ChunkFilter_1_21_5_RC2::class.java,
            ChunkFilter_24w18a::class.java,
            ChunkFilter_25w02a::class.java,
            ChunkFilter_25w32a::class.java,
            ChunkFilter_15w32a::class.java,
            ChunkFilter_Null::class.java,
        )
            .flatMap { clazz -> clazz.declaredClasses.asList() }

        fun init() {
            // initialize all implementations
            for (clazz in CHUNK_FILTER_CLASSES) {
                var interfaceClass: Class<*>? = null
                var superClass: Class<*>? = clazz
                while (interfaceClass == null && superClass != null) {
                    val interfaces = superClass.interfaces
                    if (interfaces.size > 0) {
                        interfaceClass = interfaces[0]
                    }
                    superClass = superClass.getSuperclass()
                }
                if (interfaceClass == null) {
                    // throw RuntimeException("could not find interface for " + clazz)
                    continue
                }

                implementations.compute(interfaceClass) { k: Class<*>?, v: TreeMap<Int?, Any?>? ->
                    var v = v
                    if (v == null) {
                        v = TreeMap()
                    }
                    try {
                        val clazzConstructor = clazz.getConstructor()
                        clazzConstructor.isAccessible = true
                        v[clazz.getAnnotation<MCVersionImplementation?>(MCVersionImplementation::class.java)!!.value] =
                            clazzConstructor.newInstance()
                    } catch (e: InstantiationException) {
                        throw RuntimeException(e)
                    } catch (e: IllegalAccessException) {
                        throw RuntimeException(e)
                    } catch (e: InvocationTargetException) {
                        throw RuntimeException(e)
                    } catch (e: NoSuchMethodException) {
                        throw RuntimeException(e)
                    }
                    v
                }
            }
            // Get VersionHandler.implementations field (private static final)
            // This is a hashmap, so let's individually set each entry
            val implementationsField: Field = VersionHandler::class.java.getDeclaredField("implementations")
            implementationsField.isAccessible = true
            val versionHandlerImplementations = implementationsField.get(null) as HashMap<Class<*>, TreeMap<Int?, Any?>>
            for ((key, value) in implementations) {
                versionHandlerImplementations[key!!] = value
            }
        }

    }

}