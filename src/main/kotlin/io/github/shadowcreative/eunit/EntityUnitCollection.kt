package io.github.shadowcreative.eunit

import com.google.common.collect.ArrayListMultimap
import com.google.gson.JsonElement
import io.github.shadowcreative.chadow.Activator
import io.github.shadowcreative.chadow.plugin.IntegratedPlugin
import io.github.shadowcreative.chadow.sendbox.SafetyExecutable
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

abstract class GenericInstance<U>
{
    @Suppress("UNCHECKED_CAST")
    private lateinit var persistentClass : Class<U>
    fun getPersistentClass() : Class<U> = this.persistentClass

    protected var genericInstance : U? = null

    @Deprecated("The superclass instance was not supported", ReplaceWith("this.getPersistentClass"))
    fun getSuperclassInstance() : U? = this.genericInstance

    init {
        val parameterizedType = (javaClass.genericSuperclass as? ParameterizedType)
        if (parameterizedType != null) {
            this.persistentClass = parameterizedType.actualTypeArguments[0] as Class<U>
        }
    }
}

open class EntityUnitCollection<E : EntityUnit<E>> : GenericInstance<E>, Activator<IntegratedPlugin>
{
    @Synchronized
    private fun onChangeHandler(targetClazz : Class<E>? = this.getPersistentClass()) : Map<String, Boolean>?
    {
        if(targetClazz == null)
            return null

        val result = this.onChangeHandler0(targetClazz.typeName)
        return null
    }

    @SafetyExecutable(libname = "Chadow.Internal.Core")
    private external fun onChangeHandler0(value0 : String) : String

    override fun isEnabled(): Boolean {
        return this.instancePlugin != null
    }

    override fun setEnabled(handleInstance: IntegratedPlugin)
    {
        this.instancePlugin = handleInstance
        this.setEnabled(this.instancePlugin != null)
    }

    override fun setEnabled(active: Boolean)
    {
        if(active)
        {

        }
        else
        {

        }
    }

    fun registerObject(entity: EntityUnit<E>): Boolean
    {
        var handlePlugin = this.instancePlugin
        if(this.instancePlugin == null)
            handlePlugin = IntegratedPlugin.CorePlugin

        if(handlePlugin == null)
            println("Warning: The controlled plugin was unhandled -> " + "${entity::class.java.typeName}@${entity.getUniqueId()}")
        else {
            entity.setPlugin(handlePlugin)
        }

        this.entityCollection!!.add(entity)
        return true
    }

    constructor() : this(UUID.randomUUID().toString().replace("-", ""))

    protected constructor(uuid : String) : super()
    {
        this.uuid = uuid
        this.entityCollection = ArrayList()
    }

    @Synchronized fun gerenate() : EntityUnitCollection<E>
    {
        pluginCollections.put(this.instancePlugin, this)
        return this
    }

    protected fun setIdentifiableObject(vararg fieldString : String) {
        this.identifier.addAll(fieldString)
    }

    private val uuid : String
    fun getUniqueId() : String = this.uuid


    private var instancePlugin : IntegratedPlugin? = null
    fun getPlugin() : IntegratedPlugin? = this.instancePlugin

    private var entityCollection : MutableList<EntityUnit<E>>? = null
    fun getEntities() : MutableList<EntityUnit<E>>? = this.entityCollection

    private val identifier : MutableList<String> = ArrayList()
    fun getIdentifier() : MutableList<String> = this.identifier

    open fun getEntity(objectData: Any?) : E?
    {
        if(objectData == null) return null
        @Suppress("UNCHECKED_CAST")
        return EntityUnitCollection.getEntity0(objectData, this.getPersistentClass())
    }

    companion object
    {
        private val pluginCollections : ArrayListMultimap<IntegratedPlugin, EntityUnitCollection<*>> = ArrayListMultimap.create()
        fun getEntityCollections() : ArrayListMultimap<IntegratedPlugin, EntityUnitCollection<*>> = pluginCollections

        fun <U> deserialize(element : JsonElement, reference: Class<U>) : U?
        {
            return null
        }

        fun <E> getEntity0(objectData: Any, refClazz : Class<E>): E?
        {
            try
            {
                return null
            }
            catch(e : TypeCastException)
            {
                return null
            }
        }

        fun asReference(entity: EntityUnit<*>)
        {
            for(k in getEntityCollections().values()) {
                if(entity::class.java.isAssignableFrom(k.getPersistentClass())) {
                    if(! k.isEnabled()) {
                        // Hook the reference collection.
                        var eField = entity::class.java.superclass.getDeclaredField("eCollection")
                        eField.isAccessible = true
                        eField.set(entity, k)

                        // Generate the unique signature if the entity have no id.
                        eField = entity::class.java.superclass.getDeclaredField("uuid")
                        eField.isAccessible = true
                        eField.set(entity, UUID.randomUUID().toString().replace("-", ""))
                    }
                    else {
                        val messageHandler = k.instancePlugin!!.getMessageHandler()
                        messageHandler.sendMessage("The EntityUnitCollection<${k.getPersistentClass()}> was disabled, It couldn't register your entity.")
                    }
                }
            }
            Logger.getGlobal().log(Level.WARNING, "Not exist EntityUnitCollection<${entity::class.java.simpleName}>," +
                    " It needs to specific entity collection from register class.")
        }
    }
}
