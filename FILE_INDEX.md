# 📚 Index Complet - Système Teleporter Wynvers

## 📦 Fichiers Java créés

### Mécanique Nexo Core
```
src/main/java/com/wynvers/customevents/nexo/teleporter/
├── TeleporterMechanic.java          (50 lignes)
└── TeleporterMechanicFactory.java    (42 lignes)
```
**Rôle**: Enregistre la mécanique dans Nexo et fournit les propriétés du téléporteur

### Gestion Interactive
```
src/main/java/com/wynvers/customevents/nexo/teleporter/
└── TeleporterSetupManager.java       (160 lignes)
```
**Rôle**: Gère la machine d'état des 7 étapes de configuration

### Génération Automatique
```
src/main/java/com/wynvers/customevents/nexo/teleporter/
└── TeleporterConfigGenerator.java    (58 lignes)
```
**Rôle**: Génère automatiquement les fichiers YAML Nexo

### Listeners d'Événements
```
src/main/java/com/wynvers/customevents/listener/
├── TeleporterInputListener.java      (65 lignes)
└── TeleporterEventListener.java      (135 lignes)
```
**Rôle**: Traite les événements Bukkit et les messages du joueur

### Intégration Principale
```
src/main/java/com/wynvers/customevents/
└── WynversCustomEvents.java          (215 lignes - mise à jour)
```
**Rôle**: Enregistre tous les composants du système

---

## 📖 Documentation créée

```
Root Directory/
├── TELEPORTER_GUIDE.md              → Guide utilisateur complet
├── IMPLEMENTATION_SUMMARY.md        → Résumé technique
├── QUICK_START.md                   → Guide d'installation rapide
├── FILE_INDEX.md                    → Ce fichier
└── examples/
    └── teleporter_example.yml       → Exemple de configuration
```

---

## ⚙️ Ressources d'installation

```
src/main/resources/
└── wynvers_teleporter.yml           → Configuration Nexo prête à l'emploi
```

---

## 🎯 Flux complet du système

### 1. Enregistrement (Démarrage du serveur)
```java
// WynversCustomEvents.onEnable()
├── NexoMechanicsRegisteredEvent
└── TeleporterMechanicFactory.registerMechanicFactory()
    └── MechanicsManager registre la mécanique
```

### 2. Configuration (Joueur pose le bloc base)
```
BlockPlaceEvent: teleporter_base
├── TeleporterEventListener.onBlockPlace()
└── TeleporterSetupManager.startSetup()
    └── Stage 1: AWAITING_NAME
        ├── Stage 2: AWAITING_WORLD
        ├── Stage 3: AWAITING_X
        ├── Stage 4: AWAITING_Y
        ├── Stage 5: AWAITING_Z
        ├── Stage 6: AWAITING_YAW
        └── Stage 7: AWAITING_PITCH
            └── TeleporterConfigGenerator.generateTeleporterConfig()
                └── Création du fichier teleporter_items.yml
```

### 3. Téléportation (Joueur marche sur téléporteur)
```
PlayerMoveEvent
├── TeleporterEventListener.onPlayerMove()
├── Détection du bloc téléporteur
├── Vérification du cooldown (500ms)
├── Récupération de TeleporterMechanic
└── player.teleport(destination)
```

---

## 📊 Architecture technique

### Hiérarchie des classes

```
Mechanic (Nexo)
    ↑
    └── TeleporterMechanic
        │   - destinationWorld: String
        │   - destinationX: double
        │   - destinationY: double
        │   - destinationZ: double
        │   - yaw: float
        │   - pitch: float
        └── Utilisée par: TeleporterMechanicFactory

MechanicFactory (Nexo)
    ↑
    └── TeleporterMechanicFactory
        │   - MECHANIC_ID = "teleporter"
        │   - instance: TeleporterMechanicFactory
        └── Utilisée par: WynversCustomEvents

Manager (Custom)
    └── TeleporterSetupManager
        │   - playerStates: Map<UUID, PlayerSetupState>
        │   - getCurrentStage()
        │   - advanceSetup()
        └── Utilisée par: TeleporterInputListener, TeleporterEventListener

Generator (Custom)
    └── TeleporterConfigGenerator
        │   - generateTeleporterConfig()
        │   - generateTeleporterYaml()
        └── Utilisée par: TeleporterInputListener

Listener (Bukkit)
    ├── TeleporterInputListener
    │   └── @EventHandler onPlayerChat(AsyncPlayerChatEvent)
    │
    └── TeleporterEventListener
        ├── @EventHandler onBlockPlace(BlockPlaceEvent)
        └── @EventHandler onPlayerMove(PlayerMoveEvent)
```

---

## 🔌 Intégration avec Nexo

### Item YAML attendu:
```yaml
teleporter_base:
  Mechanics:
    teleporter:
      world: "{destination_world}"
      x: "{x_coordinate}"
      y: "{y_coordinate}"
      z: "{z_coordinate}"
      yaw: "{yaw_angle}"
      pitch: "{pitch_angle}"
```

### Parsing par Nexo:
1. Nexo lit l'item YAML
2. Détecte la section `Mechanics.teleporter:`
3. Appelle `TeleporterMechanicFactory.parse(ConfigurationSection)`
4. Crée une instance `TeleporterMechanic`
5. Stocke la référence dans Nexo's mechanics manager

### Accès à la runtime:
```java
TeleporterMechanicFactory factory = TeleporterMechanicFactory.instance();
TeleporterMechanic mechanic = factory.getMechanic(itemId);
mechanic.destinationWorld()  // Accès aux coordonnées
```

---

## 📋 Checklist d'installation

- [ ] ✅ TeleporterMechanic.java créé
- [ ] ✅ TeleporterMechanicFactory.java créé
- [ ] ✅ TeleporterSetupManager.java créé
- [ ] ✅ TeleporterConfigGenerator.java créé
- [ ] ✅ TeleporterInputListener.java créé
- [ ] ✅ TeleporterEventListener.java créé
- [ ] ✅ WynversCustomEvents.java mis à jour
- [ ] ✅ wynvers_teleporter.yml ajouté
- [ ] ✅ TELEPORTER_GUIDE.md documenté
- [ ] ✅ QUICK_START.md créé
- [ ] ✅ IMPLEMENTATION_SUMMARY.md créé
- [ ] ✅ Compilation réussie (BUILD SUCCESS)
- [ ] ✅ JAR généré (target/WynversCustomEvents-1.0.0.jar)

---

## 🧪 Procédure de test

### Test 1: Enregistrement de la mécanique
```
Attendu: "[WynversCustomEvents] Registered Nexo mechanic 'teleporter'."
```

### Test 2: Création du bloc base
```
Affirmation: /give @p paper{NexoID:"teleporter_base"}
Attendu: Le bloc s'affiche dans l'inventaire du joueur
```

### Test 3: Configuration interactive
```
Affirmation: Joueur pose le bloc
Chaîne de messages:
  "§6[Teleporter] §fEntrez le nom du téléporteur:"
  "§6[Teleporter] §fEntrez le nom du monde de destination:"
  "§6[Teleporter] §fEntrez la coordonnée X:"
  "§6[Teleporter] §fEntrez la coordonnée Y:"
  "§6[Teleporter] §fEntrez la coordonnée Z:"
  "§6[Teleporter] §fEntrez le regard YAW (0):"
  "§6[Teleporter] §fEntrez le regard PITCH (0):"
```

### Test 4: Génération automatique
```
Affirmation: Configuration complétée
Attendu: 
  - Message: "Configuration sauvegardée!"
  - Fichier créé: plugins/Nexo/items/teleporter_items.yml
```

### Test 5: Téléportation
```
Affirmation: Joueur marche sur le bloc créé
Attendu: Joueur télélporté + "§a[Teleporter] Téléporté!"
```

---

## 📈 Statistiques

```
Total fichiers Java créés:     7
Total lignes Java:             ~500
Total fichiers documentation:  4
Total lignes documentation:    ~400

Compilation:
  ✅ Success
  ⚠️ Warnings (API dépréciées, normal)
  ❌ Errors: 0

JAR généré: 
  ✅ WynversCustomEvents-1.0.0.jar
```

---

## 🔐 Points clés de sécurité

- **Validation des entrées**: Numérique pour X/Y/Z/Yaw/Pitch
- **Vérification du monde**: Monde doit exister avant téléportation
- **Cooldown**: 500ms anti-spam
- **Exceptions**: Gérées pour éviter les crashs
- **Permissions**: Héritées du système Nexo/Bukkit

---

## 🚀 Prochaines étapes (Optionnel)

1. **Permissions personnalisées** - `/teleporter.create`, `/teleporter.use`
2. **Base de données** - Sauvegarde persistent des téléporteurs
3. **GUI graphique** - Interface au lieu du chat
4. **Effets visuels** - Particules de téléportation
5. **Double authentification** - Approbation admin avant création
6. **Limitations spatiales** - Zones autorisées de création

---

**Version**: 1.0.0  
**Statut**: ✅ Prêt pour production  
**Date de création**: 30/04/2026  
**Dernière mise à jour**: 30/04/2026

