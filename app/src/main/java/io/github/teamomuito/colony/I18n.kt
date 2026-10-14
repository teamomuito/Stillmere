package io.github.teamomuito.colony

/**
 * Text the player reads in the menus and settings, in English or Brazilian Portuguese. Text not in the table is shown
 * as written, so an untranslated label never disappears.
 */
object I18n {
    const val EN = "en"
    const val PT_BR = "pt-BR"

    /** The language in use. Set from the device's preferences when the app starts. */
    var lang: String = EN

    fun t(text: String): String = if (lang == PT_BR) PT[text] ?: text else text

    private val PT = mapOf(
        "Continue" to "Continuar",
        "Load game" to "Carregar jogo",
        "Quickload" to "Carregamento rápido",
        "New colony" to "Nova colônia",
        "Tutorial colony" to "Colônia com tutorial",
        "Check for updates" to "Verificar atualizações",
        "Settings" to "Configurações",
        "How to play" to "Como jogar",
        "Quit" to "Sair",
        "Done" to "Concluído",
        "Close" to "Fechar",
        "Cancel" to "Cancelar",
        "Language" to "Idioma",
        "Show the tutorial in new colonies" to "Mostrar o tutorial em novas colônias",
        "Start the tutorial over" to "Recomeçar o tutorial",
        "Replace your Continue game?" to "Substituir o jogo de Continuar?",
        "Start with the tutorial?" to "Começar com o tutorial?",
        "A colony survival sim. Survive the frontier, build a settlement, and leave." to
            "Um simulador de sobrevivência de colônia. Sobreviva na fronteira, construa um assentamento e parta.",
        "Survive on a hostile frontier, build a colony, and eventually build a ship to leave it." to
            "Sobreviva em uma fronteira hostil, construa uma colônia e, por fim, construa uma nave para partir.",
        "Saves are kept on the device." to "Os salvamentos ficam guardados no aparelho.",
    )
}
