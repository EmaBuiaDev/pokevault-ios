package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.ServerNow
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Aggiungere, cambiare e togliere carte: le stesse operazioni di
 * FirestoreRepository su Android (addCard, deleteCard, deleteCardByApiId),
 * con gli stessi campi, cosi' una carta aggiunta dall'iPhone e' identica a una
 * aggiunta da Android e l'app Android la legge senza accorgersi di niente.
 *
 * Due regole di Android tenute uguali:
 * - la stessa stampa nella stessa lingua non si duplica: se c'e' gia', sale la
 *   quantita';
 * - le carte solo-deck non si toccano e non muovono i totali dell'utente.
 */
class CollectionWriter(
    private val writes: FirestoreWrites,
    private val auth: AuthRepository,
    private val collection: CollectionRepository,
) {
    /**
     * Una carta del catalogo in collezione: addCardWithDetails di
     * SetDetailViewModel + addCard di FirestoreRepository, su Android.
     */
    suspend fun addFromCatalog(
        card: Card,
        expansionName: String,
        price: PriceEntry?,
        variant: String,
        quantity: Int,
        condition: String,
        language: String,
    ) {
        val (uid, token) = credentials()
        val apiCardId = card.italianId() ?: error("Carta senza id: ${card.cardId}")
        val canonicalLanguage = canonicalDisplayLanguage(language)
        val incoming = fieldsFor(card, expansionName, price, variant, quantity, condition, canonicalLanguage, apiCardId)

        val existing = writes.findPrints(uid, token, apiCardId, variant).firstOrNull { (_, fields) ->
            normalizeLanguageKey(fields.string("language")) == normalizeLanguageKey(canonicalLanguage) &&
                (fields.boolean("deckOnly") ?: false) == false
        }

        var effectiveValue = incoming["estimatedValue"] as Double
        if (existing != null) {
            val (cardId, current) = existing
            val currentQty = current.int("quantity") ?: 1
            if (effectiveValue <= 0.0) effectiveValue = current.double("estimatedValue") ?: 0.0
            val updates = mutableMapOf<String, Any?>(
                "quantity" to currentQty + quantity,
                "estimatedValue" to effectiveValue,
                "language" to canonicalLanguage,
            )
            // Come su Android: si sistemano i documenti vecchi salvati senza classificazione.
            val currentSupertype = normalizeCategory(current.string("supertype").orEmpty())
            val incomingSupertype = normalizeCategory(incoming["supertype"] as String)
            if (incomingSupertype != null &&
                (currentSupertype == null || (currentSupertype == "Pokémon" && incomingSupertype != "Pokémon"))
            ) updates["supertype"] = incoming["supertype"]
            val incomingSubtypes = incoming["subtypes"] as List<*>
            if ((current["subtypes"]?.let { runCatching { it.jsonArray.isEmpty() }.getOrDefault(true) } != false) && incomingSubtypes.isNotEmpty()) {
                updates["subtypes"] = incomingSubtypes
            }
            val currentType = current.string("type").orEmpty()
            val incomingType = incoming["type"] as String
            if ((currentType.isBlank() || currentType.equals("Colorless", true)) &&
                incomingType.isNotBlank() && !incomingType.equals("Colorless", true)
            ) updates["type"] = incomingType
            if ((current.int("hp") ?: 0) <= 0 && (incoming["hp"] as Int) > 0) updates["hp"] = incoming["hp"]
            writes.updateCard(uid, token, cardId, updates)
        } else {
            writes.createCard(uid, token, incoming)
        }

        bestEffortTotals(uid, token, quantity.toLong(), effectiveValue * quantity)
        collection.notifyChanged()
    }

    /**
     * Cambia le copie di una stampa posseduta: a zero la toglie. Si toccano solo
     * quantita' e totali, come removeOneCopy su Android.
     */
    suspend fun setQuantity(print: PokemonCard, quantity: Int) {
        if (quantity <= 0) return deletePrint(print)
        val (uid, token) = credentials()
        writes.updateCard(uid, token, print.id, mapOf("quantity" to quantity))
        if (!print.deckOnly) {
            val diff = quantity - print.quantity
            bestEffortTotals(uid, token, diff.toLong(), print.estimatedValue * diff)
        }
        collection.notifyChanged()
    }

    /** Toglie un documento (deleteCard su Android). */
    suspend fun deletePrint(print: PokemonCard) {
        val (uid, token) = credentials()
        writes.deleteCard(uid, token, print.id)
        if (!print.deckOnly) bestEffortTotals(uid, token, -print.quantity.toLong(), -print.estimatedValue * print.quantity)
        collection.notifyChanged()
    }

    /**
     * Toglie una carta da tutte le sue stampe, come il cestino del dettaglio su
     * Android (deleteCardByApiId). Le copie solo-deck restano: chi toglie una
     * carta dalla collezione non sta svuotando i suoi deck di prova.
     */
    suspend fun deleteAllPrints(apiCardId: String) {
        val (uid, token) = credentials()
        writes.findPrints(uid, token, apiCardId)
            .filter { (_, fields) -> fields.boolean("deckOnly") != true }
            .forEach { (cardId, fields) ->
                writes.deleteCard(uid, token, cardId)
                val quantity = fields.int("quantity") ?: 0
                bestEffortTotals(uid, token, -quantity.toLong(), -(fields.double("estimatedValue") ?: 0.0) * quantity)
            }
        collection.notifyChanged()
    }

    private suspend fun credentials(): Pair<String, String> {
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        return uid to token
    }

    /**
     * I totali in users/{uid}: su Android sono "fire-and-forget", e la UI li
     * ricalcola comunque dalle carte. Qui uguale: se falliscono, la carta resta.
     */
    private suspend fun bestEffortTotals(uid: String, token: String, cards: Long, value: Double) {
        try {
            writes.incrementTotals(uid, token, cards, value)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    companion object {
        /**
         * I campi del documento, nello stesso ordine e con gli stessi valori che
         * Android ricava da una carta del catalogo italiano (toItalianTcgCard +
         * addCardWithDetails).
         */
        fun fieldsFor(
            card: Card,
            expansionName: String,
            price: PriceEntry?,
            variant: String,
            quantity: Int,
            condition: String,
            canonicalLanguage: String,
            apiCardId: String,
        ): Map<String, Any?> = linkedMapOf(
            "name" to card.nome,
            // Come images.small su Android: l'immagine bassa del Worker col cache-buster.
            "imageUrl" to card.imageUrl(WORKER_BASE_URL + "/", size = "low")?.let { "$it&itv=r2v3" }.orEmpty(),
            "set" to expansionName,
            "rarity" to card.rarity.orEmpty(),
            // Le carte a doppio tipo arrivano come "Tipo1, Tipo2": si salva il primo.
            "type" to (card.tipo?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: "Colorless"),
            "hp" to (card.ps?.trim()?.toIntOrNull() ?: 0),
            "supertype" to supertypeOf(card),
            "subtypes" to listOfNotNull(card.stage?.trim()?.takeIf { it.isNotBlank() }),
            "isGraded" to false,
            "grade" to null,
            "gradingCompany" to "",
            // minimumEurPriceOrZero su Android: il minimo, poi la media, in euro.
            "estimatedValue" to (price?.low?.takeIf { it > 0 } ?: price?.avg?.takeIf { it > 0 } ?: 0.0),
            "quantity" to quantity,
            "condition" to condition,
            "notes" to "",
            "apiCardId" to apiCardId,
            "cardNumber" to card.number.orEmpty(),
            "variant" to variant,
            "language" to canonicalLanguage,
            "deckOnly" to false,
            "addedAt" to ServerNow,
        )

        /** ItalianCardFacets.supertypeOf su Android. */
        fun supertypeOf(card: Card): String {
            if ((card.ps?.toIntOrNull() ?: 0) > 0) return "Pokémon"
            return if (card.nome.trim().startsWith("Energia", ignoreCase = true)) "Energy" else "Trainer"
        }

        fun canonicalDisplayLanguage(language: String?): String = when (normalizeLanguageKey(language)) {
            "ITA" -> "🇮🇹 Italiano"
            "ENG" -> "🇬🇧 English"
            "JAP" -> "🇯🇵 Giapponese"
            "CHN" -> "🇨🇳 Cinese"
            else -> language?.trim()?.takeIf { it.isNotBlank() } ?: "🇬🇧 English"
        }

        fun normalizeLanguageKey(language: String?): String {
            val normalized = language.orEmpty().trim().lowercase()
            return when {
                normalized.isBlank() -> ""
                "ital" in normalized -> "ITA"
                "eng" in normalized -> "ENG"
                "jap" in normalized || "giapp" in normalized -> "JAP"
                "chn" in normalized || "chin" in normalized -> "CHN"
                else -> normalized.uppercase()
            }
        }

        private fun normalizeCategory(raw: String): String? {
            val v = raw.lowercase().trim()
            return when {
                v.contains("pok") -> "Pokémon"
                v.contains("train") || v.contains("allenat") || v.contains("aiuto") -> "Trainer"
                v.contains("energ") -> "Energy"
                else -> null
            }
        }

        private fun JsonObject.string(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        private fun JsonObject.int(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
        private fun JsonObject.double(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }
        private fun JsonObject.boolean(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
    }
}
