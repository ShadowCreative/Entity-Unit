package io.github.shadowcreative.eunit

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.shadowcreative.chadow.component.JsonCompatibleSerializer
import io.github.shadowcreative.chadow.component.adapter.FileAdapter
import io.github.shadowcreative.chadow.component.adapter.LocationAdapter
import io.github.shadowcreative.chadow.component.adapter.PlayerAdapter
import io.github.shadowcreative.chadow.component.adapter.WorldAdapter
import io.github.shadowcreative.chadow.plugin.IntegratedPlugin
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

abstract class EntityUnit<EntityType : EntityUnit<EntityType>>
{
    @Synchronized open fun create(objectId : String = uuid) : EntityUnit<EntityType>
    {
        @Suppress("LeakingThis")
        EntityUnitCollection.asReference(this)

        if(this.eCollection == null) Logger.getGlobal().log(Level.WARNING,"The reference collection unhandled")
        else this.eCollection.registerObject(this)
        return this
    }

    private val uuid : String
    fun getUniqueId() : String = this.uuid

    @Transient private var plugin : IntegratedPlugin? = null
    fun getPlugin() : IntegratedPlugin? = this.plugin
    fun setPlugin(plugin : IntegratedPlugin) { this.plugin = plugin }

    @Transient protected val eCollection : EntityUnitCollection<EntityType>? = null
    fun getEntityReference() : EntityUnitCollection<EntityType>? = this.eCollection

    fun getEntity(obj : Any?) : EntityType?
    {
        val ref = this.eCollection ?: return null
        return ref.getEntity(obj)
    }

    @Transient private val adapterColl : MutableList<JsonCompatibleSerializer<*>> = ArrayList()
    fun registerAdapter(vararg adapters : KClass<out JsonCompatibleSerializer<*>>)
    {
        for(kClass in adapters) {
            val adapterConstructor : KFunction<JsonCompatibleSerializer<*>>? = kClass.primaryConstructor
            if(adapterConstructor != null && adapterConstructor.parameters.isEmpty())
                adapterColl.add(adapterConstructor.call())

        }
    }

    fun registerAdapter(vararg adapters : Class<out JsonCompatibleSerializer<*>>)
    {
        for(kClass in adapters) {
            val adapterConstructor = kClass.constructors[0]
            if(adapterConstructor != null && adapterConstructor.parameters.isEmpty())
                adapterColl.add(adapterConstructor.newInstance() as JsonCompatibleSerializer<*>)
        }
    }

    companion object
    {
        fun setProperty(jsonObject : JsonObject, key : String, value : Any?, adapterColl : List<JsonCompatibleSerializer<*>>? = null)
        {
            val gsonBuilder = GsonBuilder()

            var adapters = adapterColl
            if(adapters == null)
                adapters = ArrayList()

            for(adapter in adapters) {
                val adapterType = adapter.getReference()
                gsonBuilder.registerTypeAdapter(adapterType, adapter)
            }
            val gson = gsonBuilder.serializeNulls().create()
            when(value) {
                is Number -> jsonObject.addProperty(key, value)
                is Char -> jsonObject.addProperty(key, value)
                is String -> jsonObject.addProperty(key, value)
                is Boolean -> jsonObject.addProperty(key, value)
                else -> {
                    if(value is EntityUnit<*>)
                    {
                        jsonObject.add(key, value.toSerialize())
                        return
                    }
                    else {
                        try {
                            val result = gson.toJson(value)
                            val parser = JsonParser()
                            val element = parser.parse(result)
                            jsonObject.add(key, element)
                        }
                        catch(e : Exception) {
                            e.printStackTrace()
                            jsonObject.addProperty(key, "FAILED_SERIALIZED_OBJECT")
                        }
                    }
                }
            }
        }
    }

    fun toSerialize() : JsonElement
    {
        return this.serialize0(this::class.java, this)
    }

    fun getEntityFields(target : Class<*> = this::class.java) : Iterable<Field>
    {
        return this.getFields0(target, true)
    }

    fun getSerializableEntityFields(target : Class<*> = this::class.java) : Iterable<Field>
    {
        return this.getFields0(target, false)
    }

    private fun getFields0(base : Class<*>, ignoreTransient: Boolean) : Iterable<Field>
    {
        val fList = ArrayList<Field>()
        var kClass : Class<*> = base
        val modifierField = Field::class.java.getDeclaredField("modifiers")
        modifierField.isAccessible = true
        while(true) {
            if(ignoreTransient)
                for(f in kClass.declaredFields) {
                    if(f.type.name.endsWith("\$Companion"))
                        continue
                    else fList.add(f)
                }
            else {
                for(f in kClass.declaredFields) {
                    f.isAccessible = true
                    if(f.type.name.endsWith("\$Companion"))
                        continue
                    val modifierInt = modifierField.getInt(f)
                    if(! Modifier.isTransient(modifierInt)) fList.add(f)
                }
            }
            if(kClass == EntityUnit::class.java) break
            kClass = kClass.superclass
        }
        return fList
    }

    private fun serialize0(fs : Class<*>, target : Any = this) : JsonElement
    {
        val jsonObject = JsonObject()
        for(f in this.getSerializableEntityFields(fs))
            addFieldProperty(jsonObject, f, target)
        return jsonObject
    }

    private fun addFieldProperty(jsonObject : JsonObject, field : Field, target : Any)
    {
        val fieldName : String = field.name
        val value: Any? = try { field.get(target) } catch(e : IllegalArgumentException) { null } catch(e2 : IllegalAccessException) { null }
        if(value == null) {
            jsonObject.addProperty(fieldName, "INVALID_SERIALIZED_VALUE"); return
        }
        setProperty(jsonObject, fieldName, value, this.adapterColl)
    }

    constructor() : this(UUID.randomUUID().toString())

    constructor(uniqueId : String)
    {
        this.registerAdapter(LocationAdapter::class, PlayerAdapter::class, WorldAdapter::class, FileAdapter::class)
        this.uuid = uniqueId
    }
}