package com.emabuia.pokevault.screens.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.model.PokemonCard
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.launch

data class AddCardUiState(
    val name: String = "",
    val set: String = "",
    val rarity: String = "Common",
    val type: String = "Fuoco",
    val hp: String = "",
    val estimatedValue: String = "",
    val quantity: String = "1",
    val condition: String = "Near Mint",
    val isGraded: Boolean = false,
    val grade: String = "",
    val gradingCompany: String = "PSA",
    val notes: String = "",
    val imageUrl: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    // Edit mode
    val isEditMode: Boolean = false,
    val editCardId: String = ""
)

/**
 * AddCardViewModel dell'app Android: una carta scritta a mano. Senza la
 * modalita' modifica, che su Android c'e' nel codice ma nessun pulsante apre
 * (onEdit del dettaglio carta non e' mai usato).
 */
class AddCardViewModel(private val writer: CollectionWriter) : ViewModel() {

    var uiState by mutableStateOf(AddCardUiState())
        private set

    fun updateName(value: String) { uiState = uiState.copy(name = value) }
    fun updateSet(value: String) { uiState = uiState.copy(set = value) }
    fun updateRarity(value: String) { uiState = uiState.copy(rarity = value) }
    fun updateType(value: String) { uiState = uiState.copy(type = value) }
    fun updateHp(value: String) { uiState = uiState.copy(hp = value.filter { it.isDigit() }) }
    fun updateEstimatedValue(value: String) {
        uiState = uiState.copy(estimatedValue = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.'))
    }
    fun updateQuantity(value: String) { uiState = uiState.copy(quantity = value.filter { it.isDigit() }) }
    fun updateCondition(value: String) { uiState = uiState.copy(condition = value) }
    fun updateIsGraded(value: Boolean) { uiState = uiState.copy(isGraded = value) }
    
    fun updateGrade(value: String) {
        // 1. Converti virgola in punto e tieni solo cifre e un punto
        val sanitized = value.replace(',', '.')
        var dotCount = 0
        val filtered = sanitized.filter { 
            if (it == '.') {
                dotCount++
                dotCount <= 1
            } else {
                it.isDigit()
            }
        }

        if (filtered.isEmpty()) {
            uiState = uiState.copy(grade = "")
            return
        }

        // 2. Controllo numerico immediato
        val gradeVal = filtered.toFloatOrNull()
        if (gradeVal != null) {
            if (gradeVal > 10f) {
                // Se è > 10, teniamo il valore precedente o resettiamo a 10
                uiState = uiState.copy(grade = "10")
                return
            }
        }
        
        uiState = uiState.copy(grade = filtered)
    }
    
    fun updateGradingCompany(value: String) { uiState = uiState.copy(gradingCompany = value) }
    fun updateNotes(value: String) { uiState = uiState.copy(notes = value) }
    fun updateImageUrl(value: String) { uiState = uiState.copy(imageUrl = value) }

    fun saveCard() {
        if (uiState.name.isBlank()) {
            uiState = uiState.copy(errorMessage = "Inserisci il nome della carta")
            return
        }

        // Validazione finale gradazione
        if (uiState.isGraded) {
            val gradeVal = uiState.grade.toFloatOrNull()
            if (gradeVal == null || gradeVal < 0 || gradeVal > 10) {
                uiState = uiState.copy(errorMessage = "Il voto deve essere compreso tra 0 e 10")
                return
            }
            if (uiState.gradingCompany.isBlank()) {
                uiState = uiState.copy(errorMessage = "Seleziona un'azienda di grading")
                return
            }
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val normalizedType = uiState.type.lowercase().trim()
            val parsedHp = uiState.hp.toIntOrNull() ?: 0
            val inferredSupertype = when {
                normalizedType.contains("energy") || normalizedType.contains("energia") -> "Energy"
                normalizedType.contains("trainer") ||
                    normalizedType.contains("supporter") ||
                    normalizedType.contains("item") ||
                    normalizedType.contains("stadium") ||
                    normalizedType.contains("tool") ||
                    normalizedType.contains("allenatore") ||
                    normalizedType.contains("aiuto") -> "Trainer"
                parsedHp > 0 -> "Pokémon"
                else -> "Trainer"
            }

            val card = PokemonCard(
                name = uiState.name.trim(),
                imageUrl = uiState.imageUrl.trim(),
                set = uiState.set.trim(),
                rarity = uiState.rarity,
                type = uiState.type,
                hp = parsedHp,
                supertype = inferredSupertype,
                subtypes = emptyList(),
                isGraded = uiState.isGraded,
                grade = if (uiState.isGraded) uiState.grade.toFloatOrNull() else null,
                gradingCompany = if (uiState.isGraded) uiState.gradingCompany else "",
                estimatedValue = uiState.estimatedValue.toDoubleOrNull() ?: 0.0,
                quantity = uiState.quantity.toIntOrNull() ?: 1,
                condition = uiState.condition,
                notes = uiState.notes.trim()
            )

            uiState = try {
                writer.addManual(card)
                uiState.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState.copy(
                    isLoading = false,
                    errorMessage = "Errore nel salvataggio: ${e.message}"
                )
            }
        }
    }


    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
