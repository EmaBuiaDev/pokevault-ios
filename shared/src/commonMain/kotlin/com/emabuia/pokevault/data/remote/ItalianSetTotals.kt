package com.emabuia.pokevault.data.remote

/**
 * Quante carte ha un'espansione italiana, per l'elenco del Pokedex: quelle del
 * nostro catalogo (expansions.card_count, verificato l'11/10/2026 uguale alle
 * carte servite per tutte le 142), cioe' le stesse che l'espansione mostra
 * aperta. Prima era il totale stampato: il 30 Anniversario diceva 128 e dentro
 * ne aveva 161.
 *
 * Il totale stampato (printedTotal, il "/128") resta un'altra cosa: serve alla
 * ricerca per numero e non passa di qui. Come CatalogRepository su Android.
 */
internal fun italianSetTotal(cardCount: Int, fallback: Int): Int =
    cardCount.takeIf { it > 0 } ?: fallback

/**
 * Un set di cui si sono appena caricate tutte le carte: si aggiorna il totale,
 * il totale stampato solo se era sconosciuto (la ricerca "67/91" ne ha bisogno).
 */
internal fun TcgSet.withKnownCardCount(cardsCount: Int): TcgSet {
    if (cardsCount <= 0) return this
    return copy(
        total = cardsCount,
        printedTotal = printedTotal.takeIf { it > 0 } ?: cardsCount
    )
}
