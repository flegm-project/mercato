# Politique de confidentialité de Mercato

Dernière mise à jour : 27 juillet 2026

Mercato est un jeu de devinettes sur les transferts de football ("nous" désigne son éditeur ;
l'identité légale de l'éditeur est précisée dans la fiche store au moment de la soumission).
Cette politique explique comment l'application traite les données, sur iOS et Android.
En résumé : le jeu ne nécessite aucun compte et fonctionne entièrement hors ligne ;
les traitements de données proviennent de services Google intégrés, la publicité
(AdMob), la mesure d'usage (Firebase Analytics) et les rapports de plantage
(Firebase Crashlytics), et vous contrôlez si les publicités sont personnalisées.

## Aucun compte, aucune inscription

Mercato ne propose ni inscription, ni connexion, ni aucun moyen de saisir des informations
personnelles. Nous ne savons pas qui vous êtes.

## Le jeu fonctionne hors ligne

L'ensemble des données du jeu (clubs, joueurs, transferts) est inclus dans l'application.
Le jeu n'envoie rien à nos serveurs : nous n'exploitons aucun serveur de jeu et ne recevons
aucune donnée de jeu. Vos scores, statistiques de jeu (parties jouées, meilleur score,
meilleure série, précision) et paramètres (son, vibration, choix de consentement,
état de la suppression des publicités) sont stockés uniquement sur votre appareil et sont
supprimés lorsque vous désinstallez l'application.

## Publicité

La version gratuite affiche des publicités diffusées par Google AdMob (Google Mobile Ads SDK).
Pour diffuser et mesurer les publicités, Google peut traiter des informations sur l'appareil
telles que l'identifiant publicitaire (IDFA sur iOS, Advertising ID sur Android), l'adresse IP
et des données agrégées d'interaction avec les publicités. Ce traitement s'effectue selon les
propres conditions de Google ; consultez [la page "Comment Google utilise les informations
provenant de sites ou d'applications qui utilisent ses
services"](https://policies.google.com/technologies/partner-sites) et la
[Politique de confidentialité de Google](https://policies.google.com/privacy).

Vos choix :

- **Dans l'Espace économique européen et au Royaume-Uni**, un formulaire de consentement
  certifié par Google (Google User Messaging Platform) est affiché avant la première partie.
  Si vous refusez la personnalisation, vous voyez le même nombre de publicités, simplement
  non personnalisées. Vous pouvez modifier votre choix à tout moment dans les Paramètres.
- **Sur iOS**, l'invite App Tracking Transparency vous permet de refuser le suivi ;
  le refus limite les publicités aux annonces non personnalisées.
- **Partout ailleurs**, l'écran de consentement intégré à l'application propose le même choix
  personnalisé / non personnalisé, modifiable à tout moment dans les Paramètres.
- **L'achat "Supprimer les publicités" désactive toutes les publicités** et, avec elles,
  le traitement de données effectué par le SDK publicitaire décrit ci-dessus.

## Mesure d'usage et rapports de plantage (Firebase)

Pour savoir si le jeu fonctionne et corriger ce qui casse, l'application utilise deux
services de Google, Firebase Analytics et Firebase Crashlytics.

**Firebase Analytics** enregistre des événements d'usage : début et fin de partie
(mode, score, réponses correctes, étoiles), abandon en cours de manche, indice utilisé,
ouverture d'un écran, étapes d'un achat et réglage du son. À ces événements s'ajoute un
identifiant d'instance propre à l'installation, qui n'est pas l'identifiant publicitaire
et ne vous identifie pas. Ces données servent uniquement à mesurer si le jeu tient
(parties terminées, difficulté des questions, taux d'abandon). Elles sont traitées par
Google et conservées jusqu'à 14 mois.

**Firebase Crashlytics** envoie un rapport lorsque l'application plante ou rencontre une
erreur qu'elle absorbe silencieusement. Le rapport contient l'état de l'appareil au
moment de l'incident (modèle, version du système, mémoire, trace technique), sans aucune
donnée personnelle. Il sert uniquement à diagnostiquer et corriger le défaut, et est
traité par Google.

La mesure d'usage reste active même si vous refusez la personnalisation des publicités,
car elle ne transporte aucun identifiant publicitaire. Votre refus (formulaire UMP dans
l'EEE et au Royaume-Uni, écran de consentement intégré ailleurs, App Tracking
Transparency sur iOS) retire les identifiants publicitaires de la mesure comme de la
publicité. La désinstallation de l'application interrompt tout nouvel envoi.

## Achats

L'achat intégré unique (Supprimer les publicités) est traité intégralement par Apple
(App Store, StoreKit) ou Google (Google Play, Play Billing). Nous n'avons jamais accès
à vos coordonnées de paiement. L'état de l'achat ("publicités supprimées") est stocké sur
votre appareil et peut être restauré via votre compte store.

## Enfants

Mercato ne s'adresse pas aux enfants de moins de 13 ans, ne collecte sciemment aucune donnée
(voir ci-dessus) et ne marque pas les demandes publicitaires comme destinées aux enfants.
Les paramètres familiaux des stores requérant une confirmation avant achat s'appliquent
à l'achat de suppression des publicités comme à l'habitude.

## Partage et conservation des données

Nous n'exploitons aucun serveur et ne stockons nous-mêmes aucune donnée personnelle, nous
n'avons donc rien à vendre. Le seul tiers traitant des données est Google, à la fois comme
fournisseur publicitaire (AdMob) et comme fournisseur de mesure d'usage et de rapports de
plantage (Firebase), selon les choix de consentement décrits ci-dessus ; les événements de
mesure sont conservés jusqu'à 14 mois.

## Vos droits

Pour tout ce que l'application stocke localement : la désinstallation de l'application le
supprime. Pour les données traitées par Google à des fins publicitaires, utilisez les contrôles
de consentement dans les Paramètres, les contrôles d'identifiant publicitaire de votre appareil
(réinitialiser ou supprimer l'identifiant), ou les outils de Google sur
[adssettings.google.com](https://adssettings.google.com). Les utilisateurs de l'EEE/RU peuvent
retirer ou modifier leur consentement à tout moment dans les Paramètres ; cela prend effet
à la prochaine demande publicitaire.

## Modifications

Si une version future modifie la façon dont l'application traite les données (par exemple,
des classements en ligne), cette politique sera mise à jour et la date "Dernière mise à jour"
sera modifiée avant la publication de cette version.

## Contact

Questions sur cette politique : {{contact}}

---

## Annexe A : Étiquettes de confidentialité Apple App Store

Réponses pour App Store Connect, section "App Privacy" :

- Données utilisées pour vous suivre : **Identifiants (Device ID / identifiant publicitaire)**
  (Identifiers / advertising identifier) - uniquement lorsque l'utilisateur autorise le suivi
  via ATT ; utilisées par Google AdMob pour la publicité personnalisée.
- Données liées à vous : **aucune** (none).
- Données non liées à vous : **Identifiants (Device ID)** (Identifiers), **Données d'utilisation
  (données publicitaires)** (Usage Data / advertising data), **Diagnostics (données de
  plantage et de performance du SDK publicitaire)** (Diagnostics / crash and performance data)
  - collectées par Google AdMob pour la publicité tierce et le bon fonctionnement de
  l'application (Third-Party Advertising, App Functionality : diffusion des publicités,
  prévention de la fraude) ; **Données d'utilisation (interactions avec le produit)**
  (Usage Data / product interaction) et **Identifiants (identifiant d'instance)**
  (Identifiers) collectées par Firebase Analytics à des fins d'**analyse** (Analytics) ;
  **Diagnostics (données de plantage et de performance)** (Diagnostics / crash and
  performance data) collectées par Firebase Crashlytics pour le **bon fonctionnement de
  l'application** (App Functionality).
- Finalités : publicité tierce (Third-Party Advertising), analyse (Analytics), bon
  fonctionnement de l'application (App Functionality).
- L'application elle-même ne collecte rien : aucune information de contact, de localisation,
  aucun contenu utilisateur, aucun historique de navigation, aucune donnée d'achat ne quitte
  les systèmes d'Apple.

## Annexe B : Formulaire Data safety Google Play

Réponses pour la section "Data safety" de la Play Console :

- Votre application collecte-t-elle ou partage-t-elle des types de données utilisateur
  requis ? **Oui** (via le Google Mobile Ads SDK).
- Données collectées : **Identifiants d'appareil ou autres** (Device or other IDs /
  Advertising ID et identifiant d'instance Firebase) ; **Interactions avec l'application**
  (App interactions / interactions publicitaires et événements d'usage) ; **Journaux de
  plantage** (Crash logs) et **Diagnostics** (Diagnostics) - collectées, non partagées
  par nous (Google agit en tant que fournisseur publicitaire via AdMob et de mesure via
  Firebase), à des fins de **publicité ou marketing** (Advertising or marketing),
  d'**analyse** (Analytics) et de **fonctionnalité de l'application** (App functionality) ;
  la publicité personnalisée est facultative (l'utilisateur peut la refuser ; l'achat de
  Supprimer les publicités arrête entièrement les demandes publicitaires). Conservation des
  événements de mesure : jusqu'à 14 mois.
- Données partagées : aucune au-delà du traitement par Google décrit ci-dessus.
- Toutes les données utilisateur collectées par votre application sont-elles chiffrées
  pendant le transit ? **Oui** (le SDK publicitaire utilise HTTPS).
- Proposez-vous aux utilisateurs un moyen de demander la suppression de leurs données ?
  **Oui** : réinitialisation/suppression de l'Advertising ID via les paramètres Android,
  ainsi que les contrôles publicitaires de Google ; l'application elle-même ne stocke
  aucune donnée personnelle.
- Audit de sécurité indépendant : non applicable (Independent security review: not applicable).
- Politique Familles : l'application n'est pas conçue pour les enfants ; public cible 13+
  (Families policy: the app is not designed for children; target audience 13+).
