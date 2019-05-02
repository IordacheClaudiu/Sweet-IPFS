package utils

import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

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