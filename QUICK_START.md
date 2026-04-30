# 🚀 Installation Rapide - Mécanique Teleporter

## ⚠️ IMPORTANT

**Le plugin fournit la mécanique, mais la configuration des items se fait DANS NEXO**

Toutes les modifications de configuration doivent être faites dans:
- `plugins/Nexo/items/` (fichiers YAML des items)
- **PAS** dans le dossier `WynversCustomEvents`

---

## 1. Vérifier que le plugin est activé

À chaque redémarrage du serveur, vérifiez que ces messages s'affichent:

```
[WynversCustomEvents] Registered Nexo mechanic 'teleporter'.
[WynversCustomEvents] Teleporter mechanic enabled.
```

## 2. CRÉER la configuration de base DANS NEXO

Créez ou modifiez un fichier dans : `plugins/Nexo/items/`

Ajoutez la configuration du bloc base `teleporter_base`:

```yaml
teleporter_base:
  itemname: "§6§lTeleporter Configuration Tool"
  material: PAPER
  Mechanics:
    custom_block:
      type: NOTEBLOCK
      custom_variation: "476"
  Pack:
    parent_model: block/cube_all
    texture: fischvogel:fv_bettersnow/snow
```

## 3. Recharger Nexo

```
/nexo reload
```

Verification: Vous devriez voir le message d'enregistrement dans les logs.

## 4. Donner le bloc au joueur

```
/give @p paper{NexoID:"teleporter_base"}
```

## 5. Configurer le téléporteur

- Le joueur tient le bloc de base
- Il le pose → Les messages de configuration apparaissent dans le chat
- Il répond aux 7 questions:
  1. Nom du téléporteur
  2. Monde de destination
  3. Coordonnée X
  4. Coordonnée Y
  5. Coordonnée Z
  6. Angle YAW
  7. Angle PITCH

## 6. Recharger Nexo à nouveau

Le plugin affichera un message indiquant d'exécuter:
```
/nexo reload
```

## 7. Utiliser le téléporteur

Une fois rechargé, donnez au joueur le nouveau bloc:
```
/give @p paper{NexoID:"nom_que_vous_avez_entré"}
```

Le joueur le pose et se tient dessus → Il est téléporté!

---

## 📝 Exemple complet

### Message 1: "Entrez le nom du téléporteur"
```
> chateau_entree
```

### Message 2: "Entrez le nom du monde"
```
> castle_world
```

### Messages 3-5: Coordonnées
```
> 100
> 65
> 200
```

### Messages 6-7: Angles
```
> 90
> 0
```

### Résultat:
- Nouvelle config: `chateau_entree` créée
- Vous exécutez: `/nexo reload`
- Vous donnez: `/give @p paper{NexoID:"chateau_entree"}`
- Le joueur pose le bloc, se tient dessus → Téléporté à castle_world 100 65 200!

---

## ⚙️ Configuration avancée

**TOUS les téléporteurs sont configurés DANS NEXO (plugins/Nexo/items/)**

Le plugin fournit simplement la mécanique "teleporter". Nexo l'utilise pour créer les items.

Pour créer d'autres téléporteurs de la même texture, ajoutez-les à vos fichiers d'items Nexo:

```yaml
mon_teleporteur_1:
  itemname: "Mon Téléporteur"
  material: PAPER
  Mechanics:
    custom_block:
      type: NOTEBLOCK
      custom_variation: "476"
    teleporter:
      world: "ma_destination"
      x: "100.5"
      y: "64"
      z: "200.5"
      yaw: "180"
      pitch: "0"
  Pack:
    parent_model: block/cube_all
    texture: fischvogel:fv_bettersnow/snow
```

---

## 🆘 Problèmes courants

| Problème | Solution |
|----------|----------|
| "Nexo not found" | Installez le plugin Nexo |
| Pas de message de configuration | Assurez-vous que l'item ID est `teleporter_base` |
| "Monde inexistant" | Vérifiez le nom du monde (case sensitive) |
| Pas de fichier généré | Vérifiez les permissions sur `plugins/Nexo/items/` |

---

**Status**: ✅ Prêt à utiliser
**Version**: 1.0.0

