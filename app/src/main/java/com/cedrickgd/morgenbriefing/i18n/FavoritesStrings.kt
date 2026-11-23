package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * "Favoriten": the star on a card's ⋮ and in the reader's bar, its confirmations, and the list of
 * starred stories (head, counter, row menu, empty state). Search, day names and "nothing found"
 * come from [HistoryStrings], whose list it shares.
 */
interface FavoritesStrings {
    val overline: String
    val title: String

    /** The button beside the search field that opens the list. */
    val openFavorites: String

    /** ⋮ and the reader's star: not starred yet. */
    val addFavorite: String

    /** ⋮ and the reader's star: starred. */
    val removeFavorite: String
    val added: String
    val removed: String

    /** Under the counter on the list's head. */
    fun saved(count: Int): String
    val emptyTitle: String
    val emptyText: String

    companion object {
        fun of(lang: AppLanguage): FavoritesStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : FavoritesStrings {
        override val overline = "Deine Sammlung"
        override val title = "Favoriten"
        override val openFavorites = "Favoriten"
        override val addFavorite = "Zu Favoriten hinzufügen"
        override val removeFavorite = "Aus Favoriten entfernen"
        override val added = "Zu Favoriten hinzugefügt"
        override val removed = "Aus Favoriten entfernt"
        override fun saved(count: Int) = "Artikel gemerkt"
        override val emptyTitle = "Noch keine Favoriten"
        override val emptyText = "Tipp im Artikel auf den Stern oder wähle im ⋮ einer Karte „Zu Favoriten hinzufügen“. Gemerkte Artikel bleiben hier, bis du den Stern entfernst."
    }

    private object En : FavoritesStrings {
        override val overline = "Your collection"
        override val title = "Favorites"
        override val openFavorites = "Favorites"
        override val addFavorite = "Add to favorites"
        override val removeFavorite = "Remove from favorites"
        override val added = "Added to favorites"
        override val removed = "Removed from favorites"
        override fun saved(count: Int) = if (count == 1) "story saved" else "stories saved"
        override val emptyTitle = "No favorites yet"
        override val emptyText = "Tap the star in an article, or pick “Add to favorites” in a card's ⋮. Saved stories stay here until you remove the star."
    }

    private object Fr : FavoritesStrings {
        override val overline = "Ta collection"
        override val title = "Favoris"
        override val openFavorites = "Favoris"
        override val addFavorite = "Ajouter aux favoris"
        override val removeFavorite = "Retirer des favoris"
        override val added = "Ajouté aux favoris"
        override val removed = "Retiré des favoris"
        override fun saved(count: Int) = if (count == 1) "article gardé" else "articles gardés"
        override val emptyTitle = "Pas encore de favoris"
        override val emptyText = "Touche l'étoile dans un article ou choisis « Ajouter aux favoris » dans le ⋮ d'une carte. Les articles gardés restent ici jusqu'à ce que tu retires l'étoile."
    }

    private object Es : FavoritesStrings {
        override val overline = "Tu colección"
        override val title = "Favoritos"
        override val openFavorites = "Favoritos"
        override val addFavorite = "Añadir a favoritos"
        override val removeFavorite = "Quitar de favoritos"
        override val added = "Añadido a favoritos"
        override val removed = "Quitado de favoritos"
        override fun saved(count: Int) = if (count == 1) "artículo guardado" else "artículos guardados"
        override val emptyTitle = "Aún no hay favoritos"
        override val emptyText = "Toca la estrella en un artículo o elige «Añadir a favoritos» en el ⋮ de una tarjeta. Los artículos guardados se quedan aquí hasta que quites la estrella."
    }

    private object It : FavoritesStrings {
        override val overline = "La tua raccolta"
        override val title = "Preferiti"
        override val openFavorites = "Preferiti"
        override val addFavorite = "Aggiungi ai preferiti"
        override val removeFavorite = "Rimuovi dai preferiti"
        override val added = "Aggiunto ai preferiti"
        override val removed = "Rimosso dai preferiti"
        override fun saved(count: Int) = if (count == 1) "articolo salvato" else "articoli salvati"
        override val emptyTitle = "Ancora nessun preferito"
        override val emptyText = "Tocca la stella in un articolo o scegli «Aggiungi ai preferiti» nel ⋮ di una scheda. Gli articoli salvati restano qui finché non togli la stella."
    }
}

@Composable
@ReadOnlyComposable
fun favoritesStrings(): FavoritesStrings = FavoritesStrings.of(LocalAppLanguage.current)
