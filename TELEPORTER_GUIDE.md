# Mécanique Teleporter - Guide d'utilisation

## ⚠️ IMPORTANTE CLARIFICATION

**Cette mécanique est fournie par le plugin WynversCustomEvents**

**MAIS les configurations des items se font DANS NEXO**

Vous modifiez: `plugins/Nexo/items/*.yml`  
Vous n'édditez JAMAIS les fichiers du plugin WynversCustomEvents

---

## Vue d'ensemble

La mécanique **teleporter** permet aux administrateurs Nexo de créer des blocs de téléportation interactifs dans les fichiers de configuration Nexo.

## Comment utiliser

### Étape 1 : Obtenir le bloc de base

Créez un item Nexo appelé `teleporter_base` dans le fichier de configuration de Nexo :

```yaml
teleporter_base:
  itemname: "§6Teleporter Configuration Tool"
  material: PAPER
  Mechanics:
    custom_block:
      type: NOTEBLOCK
      custom_variation: "476"
  Pack:
    parent_model: block/cube_all
    texture: fischvogel:fv_bettersnow/snow
```

### Étape 2 : Poser le bloc

Un joueur pose le bloc `teleporter_base`. Cela déclenche automatiquement la séquence de configuration.

### Étape 3 : Configuration interactive

Le plugin affichera 7 menus de chat successifs :

1. **Nom du téléporteur** - L'ID unique du téléporteur (ex: `donjon_1_zone_1`)
2. **Monde de destination** - Le nom du monde (ex: `donjons`)
3. **Coordonnée X** - Position X en nombres (ex: `90`)
4. **Coordonnée Y** - Position Y en nombres (ex: `120`)
5. **Coordonnée Z** - Position Z en nombres (ex: `90`)
6. **Regard YAW** - Rotation horizontale en degrés (ex: `90`)
7. **Regard PITCH** - Rotation verticale en degrés (ex: `0`)

### Étape 4 : Configuration automatique

Une fois les 7 réponses fournies, le plugin :
1. Crée automatiquement une nouvelle configuration Nexo
2. Écrit l'item généré dans `plugins/Nexo/items/teleporter_items.yml`
3. Informe le joueur d'exécuter `/nexo reload`

### Étape 5 : Utilisation du téléporteur

Après `/nexo reload` :
- Quand un joueur se tient sur le bloc de téléportation créé
- Il est automatiquement téléporté aux coordonnées configurées
- Un message de confirmation s'affiche

## Format du fichier généré

Le fichier généré ressemble à :

```yaml
donjon_1_zone_1:
  itemname: Teleporter
  material: PAPER
  Mechanics:
    custom_block:
      type: NOTEBLOCK
      custom_variation: "476"
    teleporter:
      world: "donjons"
      x: "90"
      y: "120"
      z: "90"
      yaw: "90"
      pitch: "0"
  Pack:
    parent_model: block/cube_all
    texture: fischvogel:fv_bettersnow/snow
```

## Exemple d'utilisation complet

### 1. Ajouter à Nexo
Ajoutez la configuration `teleporter_base` au fichier des items Nexo.

### 2. Donner le bloc au joueur
```
/give @p paper{NexoID:"teleporter_base"}
```

### 3. Poser le bloc
Le joueur pose le bloc. La séquence commence.

### 4. Répondre aux questions

```
[Plugin] Entrez le nom du téléporteur:
> donjon_entree_salle_boss

[Plugin] Entrez le nom du monde de destination:
> boss_world

[Plugin] Entrez la coordonnée X:
> 100.5

[Plugin] Entrez la coordonnée Y:
> 65

[Plugin] Entrez la coordonnée Z:
> 150.5

[Plugin] Entrez le regard YAW (0):
> 180

[Plugin] Entrez le regard PITCH (0):
> 0

[Plugin] Configuration sauvegardée!
[Plugin] Exécutez /nexo reload
```

### 5. Recharger Nexo
```
/nexo reload
```

### 6. Utiliser le téléporteur
```
/give @p paper{NexoID:"donjon_entree_salle_boss"}
```
Le joueur pose ce bloc et se tient dessus. Il est maintenant téléporté à boss_world 100.5 65 150.5 avec un angle de vue de 180 degrés.

## Notes techniques

- **ID unique** : Chaque téléporteur doit avoir un ID unique
- **Cooldown** : Il y a un cooldown de 500ms entre les téléportations (anti-spam)
- **Monde vérification** : Le monde doit exister, sinon la téléportation est annulée
- **Fichier de configuration** : Les configurations générées sont écrites dans `plugins/Nexo/items/teleporter_items.yml`

## Dépannage

### "Le monde de destination n'existe pas"
- Vérifiez que vous avez entré le bon nom du monde
- Assurez-vous que le monde est chargé sur le serveur

### Le téléporteur ne fonctionne pas après `/nexo reload`
- Vérifiez les logs pour les erreurs
- Assurez-vous que le fichier YAML a été généré correctement
- Vérifiez que l'ID du téléporteur est unique

### Impossible de configurer le téléporteur
- Assurez-vous d'être en mode chat normal (pas en commande)
- Tapez directement vos réponses sans la barre "/"

