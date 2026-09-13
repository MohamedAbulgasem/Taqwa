# Politique de confidentialité de Taqwa

_Dernière mise à jour le 13 septembre 2026. S’applique à Taqwa 1.0.0 et aux versions ultérieures sur Android et iOS._

Taqwa est une application islamique gratuite, sans compte, sans publicité et sans analyse. Elle
fonctionne hors ligne. La seule chose pour laquelle elle utilise Internet est le téléchargement
des récitations du Coran, et uniquement lorsque vous le lui demandez.

## Ce qui reste sur votre téléphone

Tout ce que l’application sait est enregistré sur votre appareil et nulle part ailleurs :

- votre position, ou la ville que vous avez choisie, servant à calculer les horaires de prière et la qibla ;
- vos paramètres : méthode de calcul, choix de notification, voix de l’adhan, thème, récitateur ;
- vos signets du Coran et votre position de lecture ;
- vos compteurs de Tasbih ;
- les récitations que vous avez téléchargées.

Rien de tout cela ne nous est envoyé, ni à qui que ce soit. Il n’y a aucun serveur derrière Taqwa
et aucun compte à créer. Supprimer l’application supprime tout cela.

## Position

L’application ne demande votre position que pour calculer les horaires de prière et la direction
de la qibla, et seulement si vous l’y autorisez. Choisir une ville dans la liste intégrée
fonctionne tout aussi bien et ne nécessite aucune autorisation. Votre position est utilisée sur
l’appareil et n’est jamais transmise.

## Téléchargement des récitations

Les récitations sont récupérées une sourate à la fois depuis le dépôt de données public de Taqwa
sur GitHub (github.com/MohamedAbulgasem/Taqwa-data), et uniquement lorsque vous touchez
Télécharger, que vous touchez Écouter sur une sourate que vous n’avez pas téléchargée, ou que
vous choisissez « Télécharger tout le Coran ». Par défaut, cela se fait via le Wi-Fi uniquement ;
vous pouvez autoriser les données mobiles dans Paramètres › Coran › Récitation.

Comme pour tout téléchargement, la requête montre à GitHub votre adresse IP et le fichier que
vous avez demandé, ce qui révèle le récitateur et la sourate. Elle ne comporte rien qui vous
identifie : aucun compte, aucun identifiant d’appareil, aucun cookie. La déclaration de
confidentialité de GitHub couvre ce que GitHub conserve de telles requêtes :
docs.github.com/site-policy/privacy-policies/github-general-privacy-statement.

Une fois que vous avez ouvert quoi que ce soit qui touche à la récitation, que ce soit la liste
des récitateurs, les paramètres de Récitation ou un téléchargement, l’application vérifie aussi une
fois par jour s’il existe une liste de récitateurs plus récente, depuis le même dépôt. Cette
requête est la même pour tout le monde et ne révèle rien d’autre que votre adresse IP. Tant que
vous n’utilisez pas la récitation, l’application ne fait aucune requête réseau.

Les récitations téléchargées peuvent être supprimées récitateur par récitateur dans Paramètres ›
Coran › Récitation › Téléchargements.

## Sauvegardes

Taqwa se retire de la sauvegarde d’applications d’Android : votre position et vos paramètres ne
sont donc pas copiés chez Google. Sur iOS, vos paramètres, vos signets, vos compteurs et vos
récitations téléchargées sont exclus de la sauvegarde iCloud ; les seules choses de Taqwa qui
soient sauvegardées sont les petits caches des widgets, les prochains horaires de prière et les
cent versets que le widget du verset du jour fait défiler, dont aucun ne contient votre position,
et cette sauvegarde est chiffrée par Apple et ne nous est jamais visible.

## Ce que Taqwa ne fait jamais

- Aucun compte, aucune connexion, aucun profil.
- Aucune publicité et aucun identifiant publicitaire.
- Aucune analyse, aucun rapport de plantage, aucune statistique d’utilisation.
- Aucun SDK tiers qui communique avec Internet.
- Aucune vente ni aucun partage de données, puisqu’il n’y en a aucune à vendre ou à partager.

Votre téléphone peut envoyer des rapports de plantage à Google ou à Apple selon ses propres
paramètres ; ceux-ci viennent du téléphone, et non de Taqwa, et ne contiennent rien qui vous
appartienne au-delà des détails techniques du plantage.

## Enfants

Taqwa ne collecte aucune donnée de qui que ce soit, quel que soit son âge.

## Modifications

Si une future version a besoin du réseau pour quoi que ce soit de nouveau, cette page le dira
avant la sortie de cette version, et la date ci-dessus sera mise à jour.

## Contact

Écrivez à support@taqwa.world, ou ouvrez un ticket sur github.com/MohamedAbulgasem/Taqwa/issues.
