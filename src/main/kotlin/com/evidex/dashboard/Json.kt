package com.evidex.dashboard

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Serialización/parseo JSON del dashboard vía Gson.
 *
 * Reemplaza la construcción de JSON por interpolación de strings y el parseo
 * con regex (frágiles y con riesgo de inyección si un campo contenía comillas).
 * Gson escapa correctamente y produce/valida JSON bien formado.
 */
object Json {

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .create()

    /** Serializa un valor (Map/List/primitivos/JsonElement) a texto JSON. */
    fun stringify(value: Any?): String = gson.toJson(value)

    /** Construye un objeto JSON ordenado a partir de pares clave→valor. */
    fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> =
        LinkedHashMap<String, Any?>().apply { pairs.forEach { (k, v) -> put(k, v) } }

    /**
     * Envuelve un fragmento JSON ya serializado (p. ej. `infoJson` de una
     * violación) para incrustarlo como valor sin doble-escaparlo. Devuelve un
     * objeto vacío si el fragmento no es JSON válido.
     */
    fun embed(rawJson: String?): JsonElement = try {
        JsonParser.parseString(rawJson?.ifBlank { "{}" } ?: "{}")
    } catch (_: Exception) {
        JsonObject()
    }

    /**
     * Parsea el cuerpo de una petición a un mapa plano de strings. Soporta
     * tanto JSON (`{"k":"v"}`) como pares ya extraídos. Valores no string se
     * convierten a su representación textual. Devuelve mapa vacío si no es JSON.
     */
    fun parseObjectToStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val el = JsonParser.parseString(raw)
            if (!el.isJsonObject) return emptyMap()
            buildMap {
                for ((k, v) in el.asJsonObject.entrySet()) {
                    if (v.isJsonNull) continue
                    val s = if (v is JsonPrimitive && v.isString) v.asString else v.toString()
                    put(k, s)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
