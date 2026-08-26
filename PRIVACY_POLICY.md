# Politique de Confidentialité — Dictaphone IA

**Dernière mise à jour : 26 Août 2026**

La présente Politique de Confidentialité décrit la manière dont l'application mobile **Dictaphone IA** (« nous », « notre application ») collecte, utilise, stocke et protège les données de ses utilisateurs.

Nous attachons une importance capitale à la confidentialité et à la sécurité de vos données personnelles conformément aux exigences du **Google Play Store** et du **Règlement Général sur la Protection des Données (RGPD)**.

---

## 1. Données collectées et autorisations requises

### A. Microphone (`android.permission.RECORD_AUDIO`)
* **Utilisation :** L'application nécessite l'accès au microphone uniquement lorsque vous démarrez explicitement un enregistrement vocal.
* **Stockage :** Les fichiers audio enregistrés sont stockés localement dans le répertoire privé de l'application sur votre appareil (`context.filesDir`).
* **Non-transmission sans consentement :** Aucun enregistrement en arrière-plan ou à votre insu n'est effectué.

### B. Transcriptions et Contenus Textuels
* Les paroles retranscrites et les livrables structurés (e-mails professionnels, comptes-rendus avec checklists, posts éditoriaux) sont enregistrés dans une base de données locale sécurisée sur votre téléphone (Room SQLite).

### C. Connexion Internet (`android.permission.INTERNET`)
* Utilisée exclusivement pour envoyer les requêtes de structuration vers les serveurs de l'Intelligence Artificielle (Google Gemini / Vertex AI) via un protocole chiffré (HTTPS/TLS).

---

## 2. Traitement par Intelligence Artificielle

* **Finalité :** Vos transcriptions vocales sont traitées par des modèles d'IA générative uniquement pour convertir vos notes orales en documents écrits exploitables.
* **Sécurité :** Les transmissions réseau sont chiffrées de bout en bout.
* **Non-utilisation pour l'entraînement :** Les données transmises via les API professionnelles ne sont pas utilisées pour entraîner publiquement des modèles tiers sans votre accord.

---

## 3. Absence de Partage ou de Revente de Données

* Nous ne vendons, ne louons et ne partageons **aucune de vos données personnelles ou vocales** à des régies publicitaires, courtiers de données ou tiers commerciaux.
* Aucune publicité ciblée ou traceur intrusif n'est intégré dans l'application.

---

## 4. Droit à l'effacement et Contrôle total (RGPD)

Vous disposez d'un contrôle absolu sur vos données :
1. **Suppression unitaire :** Vous pouvez supprimer n'importe quelle note et son fichier audio associé d'un simple clic depuis l'écran *Historique*. Le fichier physique est alors immédiatement détruit de votre appareil.
2. **Suppression totale :** Dans l'écran *FAQ & Aide > Données personnelles*, un bouton **« Supprimer toutes mes notes et données »** purge instantanément et définitivement l'ensemble de vos enregistrements, transcriptions et métadonnées.

---

## 5. Modifications de la présente politique

Nous pouvons mettre à jour cette Politique de Confidentialité pour refléter des évolutions légales ou techniques. Toute modification sera publiée directement dans l'application ou sur notre page officielle.

---

## 6. Contact

Pour toute question concernant cette Politique de Confidentialité ou pour exercer vos droits, vous pouvez contacter l'équipe de développement à l'adresse support :
* **Email de contact :** support@vocalflow-app.com (ou votre adresse de contact développeur Google Play Console)
