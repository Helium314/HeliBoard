package helium314.keyboard.latin.utils

object TranslatorUtils {
    // Exemple : met tout le texte en majuscule
    @JvmStatic
    fun toUpperCase(text: String?): String {
        return text?.uppercase() ?: ""
    }
    // Tu pourras ajouter ici d'autres fonctions de traduction par langue
}

