package io.github.teamomuito.colony.sim

/**
 * Content that stays in the definitions, so existing saves still load, but is never offered in the game. It is either
 * not in the base game, or it could not be confirmed as base-game content. Remove an entry here to bring it back.
 */
object BaseContent {
    /** Lever-action rifle: not found in the base game. Psychite tea: not confirmed as base-game content. */
    val hiddenItems: Set<ItemType> = setOf(ItemType.W_LEVER, ItemType.PSYCHITE_TEA)
    val hiddenRecipes: Set<Recipe> = setOf(Recipe.MAKE_LEVER, Recipe.BREW_TEA)

    /** Recipes a player can see and queue. */
    fun offered(r: Recipe): Boolean = r !in hiddenRecipes && r.out !in hiddenItems
}
