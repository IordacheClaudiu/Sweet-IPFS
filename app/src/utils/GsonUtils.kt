package utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import java.util.*
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer



class DateDeserializer : JsonDeserializer<Date> {

    override fun deserialize(json: JsonElement? ,
                             typeOfT: Type? ,
                             context: JsonDeserializationContext?): Date? {
        return if (json == null) null else Date(json.asLong)
    }
}

class DateSerializer : JsonSerializer<Date> {
    override fun serialize(src: Date? ,
                           typeOfSrc: Type? ,
                           context: JsonSerializationContext?): JsonElement? {
        return if (src == null) null else JsonPrimitive(src.time)
    }
}