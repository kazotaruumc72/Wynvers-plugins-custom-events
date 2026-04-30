# ✅ CHECKLIST PRÉ-TEST
Avant de tester le plugin, vérifiez cette checklist.

---

## 📋 Phase 1 : Installation

- [ ] ✅ JAR `WynversCustomEvents-1.0.0.jar` compilé avec succès
- [ ] ✅ JAR placé dans `plugins/`
- [ ] ✅ Serveur redémarré
- [ ] ✅ Messages de démarrage visibles:
  ```
  [WynversCustomEvents] Nexo integration enabled.
  [WynversCustomEvents] Registered Nexo mechanic 'teleporter'.
  [WynversCustomEvents] Teleporter mechanic enabled.
  [WynversCustomEvents] v1.0.0 enabled.
  ```

---

## 📋 Phase 2 : Configuration NEXO (IMPORTANT!)

- [ ] ✅ Dossier `plugins/Nexo/items/` existe
- [ ] ✅ Au moins un fichier `.yml` créé dans ce dossier
  - Exemple: `plugins/Nexo/items/teleporters.yml`
- [ ] ✅ Configuration `teleporter_base` ajoutée au fichier YAML:
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
- [ ] ✅ `/nexo reload` exécuté
- [ ] ✅ Message de démarrage Nexo visible dans les logs

---

## 📋 Phase 3 : Item création

- [ ] ✅ Exécuter en console ou en-jeu:
  ```
  /give @p paper{NexoID:"teleporter_base"}
  ```
- [ ] ✅ Joueur a le bloc dans son inventaire
- [ ] ✅ Bloc affiche le bon nom et la bonne texture

---

## 📋 Phase 4 : Configuration interactive

- [ ] ✅ Joueur pose le bloc
- [ ] ✅ Message s'affiche dans le chat:
  ```
  [Teleporter] Entrez le nom du téléporteur:
  ```
- [ ] ✅ Joueur tape : `mon_premier_tp`
- [ ] ✅ Prochain message s'affiche:
  ```
  [Teleporter] Entrez le nom du monde de destination:
  ```
- [ ] ✅ Joueur tape : `world`
- [ ] ✅ Messages continuent pour X, Y, Z, YAW, PITCH
- [ ] ✅ Exemple complet de réponses:
  ```
  mon_premier_tp
  world
  100
  65
  200
  0
  0
  ```

---

## 📋 Phase 5 : Génération du fichier

- [ ] ✅ Message final s'affiche:
  ```
  Configuration sauvegardée!
  Exécutez /nexo reload
  ```
- [ ] ✅ Fichier créé: `plugins/Nexo/items/teleporter_items.yml`
- [ ] ✅ Contenu du fichier contient la configuration:
  ```yaml
  mon_premier_tp:
    itemname: Teleporter
    material: PAPER
    Mechanics:
      custom_block:
        type: NOTEBLOCK
        custom_variation: "476"
      teleporter:
        world: "world"
        x: "100"
        y: "65"
        z: "200"
        yaw: "0"
        pitch: "0"
    Pack:
      parent_model: block/cube_all
      texture: fischvogel:fv_bettersnow/snow
  ```

---

## 📋 Phase 6 : Reload Nexo

- [ ] ✅ Exécuter: `/nexo reload`
- [ ] ✅ Nexo recharge sans erreur
- [ ] ✅ Nouveau item `mon_premier_tp` détecté par Nexo

---

## 📋 Phase 7 : Téléportation

- [ ] ✅ Exécuter:
  ```
  /give @p paper{NexoID:"mon_premier_tp"}
  ```
- [ ] ✅ Joueur reçoit le bloc
- [ ] ✅ Joueur pose le bloc
- [ ] ✅ Joueur marche sur le bloc
- [ ] ✅ Joueur est téléporté à `world 100 65 200`
- [ ] ✅ Message s'affiche:
  ```
  [Teleporter] Téléporté!
  ```

---

## 📋 Phase 8 : Tests supplémentaires

### Test 2 : Multi-monde
- [ ] ✅ Créer un téléporteur vers un autre monde existant
- [ ] ✅ Teleporter fonctionne vers le bon monde

### Test 3 : Angles de vue
- [ ] ✅ Créer un téléporteur avec YAW=90, PITCH=45
- [ ] ✅ Vérifier que le joueur regarde dans la bonne direction

### Test 4 : Cooldown
- [ ] ✅ Marcher sur le téléporteur
- [ ] ✅ Marcher dessus immédiatement à nouveau
- [ ] ✅ Cooldown de 500ms prevent la double téléportation

### Test 5 : Monde inexistant
- [ ] ✅ Créer un téléporteur vers un monde inexistant
- [ ] ✅ Message d'erreur s'affiche:
  ```
  [Teleporter] Monde inexistant: ...
  ```

---

## 📋 Résumé de la structure correcte

```
plugins/
├── plugins/WynversCustomEvents-1.0.0.jar     ✅ Plugin compilé
└── plugins/Nexo/
    └── plugins/Nexo/items/
        ├── teleporters.yml                   ✅ Fichier créé par VOUS
        │   ├── teleporter_base: {...}        ✅ Bloc de base
        │   └── ... (d'autres items)
        │
        └── teleporter_items.yml              ✅ Fichier créé AUTOMATIQUEMENT par plugin
            └── mon_premier_tp: {...}        ✅ Item généré
```

---

## ⚠️ Points critiques à vérifier

1. **Ne JAMAIS modifier** `src/main/resources/wynvers_teleporter.yml`
   - Ce fichier est un exemple seulement
   - Créer VOS fichiers dans `plugins/Nexo/items/`

2. **Toujours exécuter** `/nexo reload` après:
   - Création d'un nouvel item
   - Modification d'un item existant
   - Création du bloc de base

3. **Vérifier les logs** pour les erreurs:
   - "Nexo not found" → Installer Nexo
   - "Monde inexistant" → Vérifier l'orthographe du monde

---

## 🚩 Erreurs connues et solutions

| Erreur | Cause | Solution |
|--------|-------|----------|
| "Configuration interactive ne démarre pas" | Item ID incorrect | Utiliser `teleporter_base` exactement |
| "Pas de téléportation" | `/nexo reload` oublié | Exécuter `/nexo reload` |
| "Monde inexistant" | Monde mal épelé | Vérifier `plugins/Nexo/items/*.yml` |
| "Fichier teleporter_items.yml pas créé" | Permissions | Vérifier les permissions du dossier Nexo |

---

## ✅ Quand passer en production

Seulement si TOUS les tests de la Phase 1-7 sont ✅

Si tout est ✅, le plugin est prêt pour vos joueurs!

---

**Version**: 1.0.0  
**Date**: 30/04/2026  
**Status**: À tester

