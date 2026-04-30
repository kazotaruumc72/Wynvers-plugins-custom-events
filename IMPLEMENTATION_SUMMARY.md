# 🎯 Implémentation Complète de la Mécanique Teleporter

## ✅ Résumé d'implémentation

La mécanique **teleporter** a été complètement intégrée au plugin WynversCustomEvents. Voici ce qui a été créé :

### Fichiers créés

#### 1. **Mécanique Nexo Core** 
- `TeleporterMechanic.java` - Classe mécanque étendant Nexo pour les propriétés du téléporteur
- `TeleporterMechanicFactory.java` - Fabrique pour enregistrer la mécanique dans Nexo

#### 2. **Gestion de Configuration Interactive**
- `TeleporterSetupManager.java` - Gère la machine d'état pour les 7 étapes de configuration
  - AWAITING_NAME → Nom/ID du téléporteur
  - AWAITING_WORLD → Monde de destination
  - AWAITING_X → Coordonnée X
  - AWAITING_Y → Coordonnée Y
  - AWAITING_Z → Coordonnée Z
  - AWAITING_YAW → Angle de regard horizontal
  - AWAITING_PITCH → Angle de regard vertical

#### 3. **Génération Automatique de Configuration**
- `TeleporterConfigGenerator.java` - Génère les fichiers YAML Nexo automatiquement
  - Crée `plugins/Nexo/items/teleporter_items.yml`
  - Format conforme aux standards Nexo

#### 4. **Listeners d'Événements**
- `TeleporterInputListener.java` - Écoute les messages de chat du joueur
  - Traite les 7 entrées séquentielles
  - Génère la configuration automatiquement
  - Notifie le joueur

- `TeleporterEventListener.java` - Gère la placement et la téléportation
  - Détecte quand un joueur pose un bloc `teleporter_base`
  - Initie la séquence de configuration
  - Détecte quand un joueur marche sur un téléporteur
  - Téléporte le joueur avec cooldown

#### 5. **Intégration Principale**
- `WynversCustomEvents.java` - Mise à jour pour :
  - Enregistrer `TeleporterMechanicFactory` via `NexoMechanicsRegisteredEvent`
  - Initialiser et enregistrer `TeleporterInputListener`
  - Initialiser et enregistrer `TeleporterEventListener`

### Documentation et Exemples
- `TELEPORTER_GUIDE.md` - Guide complet d'utilisation
- `examples/teleporter_example.yml` - Exemple de configuration

---

## 🚀 Flux d'utilisation complet

```
[1] Joueur obtient: teleporter_base
         ↓
[2] Joueur pose le bloc
         ↓
[3] Plugin déclenche la séquence de 7 menus de chat
         ↓
    Entrez le nom du téléporteur
         ↓
    Entrez le monde de destination
         ↓
    Entrez les coordonnées X, Y, Z
         ↓
    Entrez les angles YAW, PITCH
         ↓
[4] Plugin génère automatiquement item_id.yml dans teleporter_items.yml
         ↓
[5] Admin exécute /nexo reload
         ↓
[6] Joueur pose le nouveau bloc de téléporteur
         ↓
[7] Joueur se tient sur le bloc → Téléportation instantanée!
```

---

## 📋 Configuration YAML exemple

### Item base (à ajouter à Nexo)
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

### Item généré automatiquement
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

---

## 🔧 Architecture technique

### Enregistrement de la mécanique
```
NexoMechanicsRegisteredEvent
    ↓
TeleporterMechanicFactory.registerMechanicFactory()
    ↓
Nexo détecte les items avec "Mechanics.teleporter:"
    ↓
TeleporterMechanic extrait les paramètres (world, x, y, z, yaw, pitch)
```

### Flux de configuration
```
BlockPlaceEvent (teleporter_base)
    ↓
TeleporterEventListener.onBlockPlace()
    ↓
setupManager.startSetup(player)
    ↓
AsyncPlayerChatEvent
    ↓
TeleporterInputListener.onPlayerChat()
    ↓
setupManager.advanceSetup(player, input)
    ↓
[répéter 7 fois]
    ↓
configGenerator.generateTeleporterConfig()
    ↓
Écrit dans teleporter_items.yml
```

### Flux de téléportation
```
PlayerMoveEvent
    ↓
TeleporterEventListener.onPlayerMove()
    ↓
findTeleporterAt(location)
    ↓
TeleporterMechanicFactory.getMechanic(itemId)
    ↓
teleportPlayer(player, mechanics)
    ↓
player.teleport(destination)
```

---

## ⚙️ Paramètres techniques

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| Cooldown de téléportation | 500ms | Empêche le spam de téléportation |
| Type de bloc | NOTEBLOCK | Type de bloc personnalisé Nexo |
| Variation | 476 | Variation de bloc personnalisé |
| Fichier de sortie | `teleporter_items.yml` | Où sont écrites les configs générées |

---

## ✔️ Compilation

✅ **BUILD SUCCESS**
- 26 fichiers source compilés
- 0 erreurs critiques
- Quelques avertissements sur API dépréciées (normal)
- JAR généré: `target/WynversCustomEvents-1.0.0.jar`

---

## 📦 Déploiement

1. Placez le JAR compilé dans `plugins/`
2. Redémarrez le serveur
3. Créez l'item `teleporter_base` dans la configuration Nexo
4. Utilisez `/give @p paper{NexoID:"teleporter_base"}`
5. Suivez le guide `TELEPORTER_GUIDE.md` pour l'utilisation

---

## 🎨 Fonctionnalités principales

✅ Configuration interactive via chat (7 étapes)  
✅ Génération automatique de fichiers YAML Nexo  
✅ Téléportation instantanée au déplacement sur le bloc  
✅ Cooldown anti-spam (500ms)  
✅ Validation des coordonnées et du monde  
✅ Messages de feedback au joueur  
✅ Logs détaillés des erreurs  
✅ Architecture extensible et maintenable  

---

## 🐛 Dépannage

### "Le monde n'existe pas"
→ Vérifiez que le monde est chargé sur le serveur

### Le téléporteur ne fonctionne pas
→ Exécutez `/nexo reload` après configuration

### Erreur lors de la génération
→ Vérifiez les permissions du dossier `plugins/Nexo/items/`

---

## 📖 Fichiers de ressource

- `TELEPORTER_GUIDE.md` - Guide utilisateur complet
- `examples/teleporter_example.yml` - Exemple de configuration
- Tous les fichiers Java sont documentés avec javadoc

---

**Status**: ✅ Prêt pour la production
**Version**: 1.0.0
**Date**: 30/04/2026

