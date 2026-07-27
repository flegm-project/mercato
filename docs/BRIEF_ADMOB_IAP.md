# Brief : configuration AdMob + achat intégré pour Mercato

Contexte : Mercato est un jeu mobile de quiz sur les transferts de football
(iOS + Android). Monétisation : publicités AdMob par défaut, un seul achat
intégré non-consommable à 3,99 € qui retire toutes les pubs. Aucun autre
produit, pas de rewarded.

Identifiant d'app prévu (les deux plateformes) : `com.nicogaray.mercato`.
Si un autre bundle ID est déjà réservé, le signaler avant de continuer.

## Tâche 1 : AdMob, créer 2 apps

Dans la console AdMob (apps.admob.com), compte de Nico :

1. Ajouter une app iOS : nom "Mercato", plateforme iOS. L'app n'est pas
   encore publiée sur l'App Store : choisir "app non listée / pas encore
   publiée", elle sera liée à la fiche App Store plus tard.
2. Ajouter une app Android : nom "Mercato", plateforme Android, même logique
   (liaison Play Store plus tard).

## Tâche 2 : 4 ad units par app (8 au total)

Créer exactement ces ad units, mêmes noms sur les deux apps :

| Nom | Format AdMob | Usage dans le jeu |
| --- | --- | --- |
| mercato_banner | Bannière | Écrans de menu (Home, Profil), 320x50 |
| mercato_sponsor | Bannière | Panneau statique sous la carte de jeu |
| mercato_interstitial | Interstitiel | Entre la dernière question et le récap |
| mercato_rectangle | Bannière (Medium Rectangle 300x250) | Écran récap |

Réglages : pas de rewarded, pas de format vidéo dédié, paramètres par défaut
sinon. Ne rien activer d'autre (pas de médiation à ce stade).

## Tâche 3 : App Store Connect, achat intégré (dès que le compte est actif)

Dans App Store Connect, sur l'app Mercato (bundle `com.nicogaray.mercato`,
créer la fiche app si elle n'existe pas encore) :

1. Créer un achat intégré de type **non-consommable**.
2. Product ID exact : `mercato_remove_ads` (immuable, ne pas improviser).
3. Prix : palier 3,99 €.
4. Localisations :
   - FR : nom "Supprimer les publicités", description "Retire définitivement
     toutes les publicités du jeu."
   - EN : nom "Remove ads", description "Permanently removes all ads from
     the game."
   - ES : nom "Eliminar anuncios", description "Elimina permanentemente
     todos los anuncios del juego."

Plus tard, même produit côté Play Console (produit ponctuel
`mercato_remove_ads`, 3,99 €), hors périmètre de ce brief si l'app Android
n'a pas encore de fiche.

## À restituer impérativement à la fin

Sans ces identifiants, le développement ne peut pas se brancher :

- App ID AdMob iOS (format `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`)
- App ID AdMob Android (même format)
- Les 8 IDs d'ad units (format `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`),
  chacun associé à son nom et sa plateforme
- Confirmation que le product ID `mercato_remove_ads` est créé, et son état
  (brouillon, en attente de review, prêt)

## Garde-fous

- Ne rien supprimer ni modifier d'existant dans les comptes.
- Aucun paiement ni souscription d'option payante.
- En cas de doute (champ inconnu, demande de vérification d'identité,
  contrat à accepter), s'arrêter et demander à Nico au lieu de deviner.
