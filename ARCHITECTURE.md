# 🔧 Clarification - Architecture du Plugin WynversCustomEvents

## ⚠️ POINT CRUCIAL

Ce plugin fournit des **MÉCANIQUES PERSONNALISÉES** pour Nexo, mais ne contient PAS les configurations des items.

### Le plugins vs Nexo

```
┌─────────────────────────────────────────────┐
│         WynversCustomEvents Plugin          │
│  (Code source et mécaniques Java compilées) │
├─────────────────────────────────────────────┤
│  • TeleporterMechanic (définition)          │
│  • FarmerMechanic (définition)              │
│  • HarvesterMechanic (définition)           │
│  • WitherPropertiesMechanic (définition)    │
│  • [Autres mécaniques...]                   │
└─────────────────────────────────────────────┘
              ↓ (utilise)
┌─────────────────────────────────────────────┐
│            Plugin Nexo                      │
│   (Lit et gère les items/configurations)    │
├─────────────────────────────────────────────┤
│  • Charge: plugins/Nexo/items/*.yml         │
│  • Utilise les mécaniques du plugin         │
│  • Crée les items réels                     │
└─────────────────────────────────────────────┘
              ↓ (gère)
┌─────────────────────────────────────────────┐
│            Items en-jeu                     │
│  (Blocks teleporteurs, culturas, etc.)      │
└─────────────────────────────────────────────┘
```

---

## 📝 OÙ CONFIGURER QUOI

### ❌ NE PAS MODIFIER
- Les fichiers du plugin WynversCustomEvents
- Les fichiers Java source
- Les fichiers `.jar`
- `src/main/resources/wynvers_teleporter.yml` (c'est un exemple)

### ✅ MODIFIER UNIQUEMENT
- **`plugins/Nexo/items/`** - Tous les fichiers d'items Nexo
  - C'est ICI que vous configurez les téléporteurs
  - C'est ICI que vous configurez les cultures (farmer)
  - C'est ICI que vous configurez tous les items avec mécaniques

---

## 🔄 Flux de travail

### Pour créer un nouveau téléporteur

```
1. Éditer: plugins/Nexo/items/mes_items.yml
   (ou créer un nouveau fichier .yml dans ce dossier)

2. Ajouter la configuration YAML:
   mon_teleporteur:
     itemname: "Teleporter"
     material: PAPER
     Mechanics:
       custom_block:
         type: NOTEBLOCK
         custom_variation: "476"
       teleporter:
         world: "destination_world"
         x: "100"
         y: "64"
         z: "200"
         yaw: "90"
         pitch: "0"
     Pack:
       parent_model: block/cube_all
       texture: mon_texture

3. Exécuter: /nexo reload

4. Item créé et prêt à l'emploi!
```

### Pour modifier un téléporteur existant

```
1. Éditer: plugins/Nexo/items/mes_items.yml
2. Modifier les valeurs (world, x, y, z, yaw, pitch)
3. Exécuter: /nexo reload
4. Changements appliqués!
```

---

## 📦 Fichiers du plugin WynversCustomEvents

Ces fichiers sont **LIVRABLES PURS** - ne doivent pas être modifiés:

```
target/WynversCustomEvents-1.0.0.jar
  ├── com/wynvers/customevents/nexo/teleporter/
  │   ├── TeleporterMechanic.class
  │   ├── TeleporterMechanicFactory.class
  │   ├── TeleporterSetupManager.class
  │   └── TeleporterConfigGenerator.class
  │
  ├── com/wynvers/customevents/listener/
  │   ├── TeleporterInputListener.class
  │   └── TeleporterEventListener.class
  │
  ├── [Autres mécaniques...]
  │
  └── plugin.yml
```

Cet artifact est auto-contenu et ne demande PAS de modification.

---

## 🎯 Résumé pour les administrateurs

| Action | Où faire | Fichier | Commande après |
|--------|---------|--------|----------------|
| Installer le plugin | `plugins/` | `WynversCustomEvents-1.0.0.jar` | Redémarrer serveur |
| Créer un teleporteur | Nexo items | `plugins/Nexo/items/*.yml` | `/nexo reload` |
| Modifier un teleporteur | Nexo items | `plugins/Nexo/items/*.yml` | `/nexo reload` |
| Ajouter une mécanique | ❌ Pas possible | Nécessite code | Recompiler le plugin |
| Recharger actions OreStack | Wcereload | OreStack conf | `/wcereload` |

---

## 💡 Cas d'usage courants

### "Je veux créer 3 teleporteurs"
**Solution**: Ajouter 3 sections dans `plugins/Nexo/items/*.yml` + `/nexo reload`

### "Je veux changer la destination d'un teleporteur"
**Solution**: Éditer `plugins/Nexo/items/*.yml` + `/nexo reload`

### "Je veux améliorer la mécanique teleporter"
**Solution**: Cloner le repo, modifier le code Java, recompiler avec Maven

### "Je veux ajouter une nouvelle mécanique"
**Solution**: Ajouter les classes Java, enregistrer dans WynversCustomEvents.java, recompiler

---

## 🚀 Mises à jour futures

Si vous modifiez le code Java du plugin:

```bash
cd Wynvers-plugins-custom-events
git pull          # (si vous utilisez Git)
mvn clean package # Recompiler
cp target/*.jar plugins/
/reload
```

**Vous ne modifiez PAS les items Nexo** - ceux-ci continuent de fonctionner.

---

## ✅ Checklist de compréhension

- [ ] ✅ Le plugin fournit les mécaniques (code)
- [ ] ✅ Nexo fournit les items (configuration YAML)
- [ ] ✅ Les modifications d'items se font dans: `plugins/Nexo/items/`
- [ ] ✅ Après modification: `/nexo reload`
- [ ] ✅ Le plugin ne demande pas de modification pour créer des items
- [ ] ✅ Les modifications du code demandent une recompilation Maven

---

**Merci cette clarification était importante! Préciser cette séparation des responsabilités.**

Version: 1.0.0  
Date: 30/04/2026

