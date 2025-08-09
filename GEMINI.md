Ce projet se decompose en deux sous projets:
1- llamaCpp: projet c++ qui contient la logique d'appel à llama-cpp , tout en assurant l'interopérabilité JNI.
2- LlamaLLmLocal: projet kotlin qui appelle la librairie generé par llama cpp, pour efectuer des predictions depuis des modèles LLM local au format gguf.
J'aimerais mettre en place une appli kotlin un peu comme ChatGpt avec une interface de chat et une possibilité de selectioner un modele local. L'utilisateur aura la posiibilité de voir l'historique de ses conversations et continuer le chat. Il pourra aussi changer de modele en cours de route. L'application aura donc un ecran de configuration du modele et un ecran de chat avec une reponse qui s'affiche au fur et a mesure des tokens générés.

voici l'architecture du projet:
   * data: Contient probablement les modèles de données et les dépôts.
   * jni: Code de pont JNI.
   * ui: Composants d'interface utilisateur (Jetpack Compose).
   * viewmodel: ViewModels pour l'interface utilisateur.
   * LlamaApi.kt: L'API principale pour interagir avec le modèle Llama.
   * MainActivity.kt: Le point d'entrée de l'application.