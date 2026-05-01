package com.wynvers.customevents.nexo.teleporter;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the setup state machine for teleporter configuration.
 * Tracks which stage of configuration each player is in (name, world, x, y, z, yaw, pitch).
 */
public class TeleporterSetupManager {

    public enum SetupStage {
        IDLE,
        AWAITING_NAME,
        AWAITING_ITEMNAME,
        AWAITING_WORLD,
        AWAITING_X,
        AWAITING_Y,
        AWAITING_Z,
        AWAITING_YAW,
        AWAITING_PITCH,
        COMPLETED
    }

    private static class PlayerSetupState {
        SetupStage stage = SetupStage.IDLE;
        String teleporterName;
        String itemname;
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    private final Map<UUID, PlayerSetupState> playerStates = new HashMap<>();

    public void startSetup(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = new PlayerSetupState();
        state.stage = SetupStage.AWAITING_NAME;
        playerStates.put(uuid, state);
        player.sendMessage("§6[Teleporter] §fEntrez le nom du téléporteur:");
    }

    public boolean isInSetup(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null && state.stage != SetupStage.IDLE && state.stage != SetupStage.COMPLETED;
    }

    public SetupStage getCurrentStage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.stage : SetupStage.IDLE;
    }

    public void advanceSetup(Player player, String input) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        if (state == null) return;

        try {
            switch (state.stage) {
                case AWAITING_NAME:
                    if (input.trim().isEmpty()) {
                        player.sendMessage("§c[Teleporter] Le nom ne peut pas être vide!");
                        return;
                    }
                    state.teleporterName = input.trim();
                    state.stage = SetupStage.AWAITING_ITEMNAME;
                    player.sendMessage("§6[Teleporter] §fEntrez le nom d'affichage (itemname) visible dans Nexo:");
                    break;

                case AWAITING_ITEMNAME:
                    if (input.trim().isEmpty()) {
                        player.sendMessage("§c[Teleporter] Le nom d'affichage ne peut pas être vide!");
                        return;
                    }
                    state.itemname = input.trim();
                    state.stage = SetupStage.AWAITING_WORLD;
                    player.sendMessage("§6[Teleporter] §fEntrez le nom du monde de destination:");
                    break;

                case AWAITING_WORLD:
                    if (input.trim().isEmpty()) {
                        player.sendMessage("§c[Teleporter] Le monde ne peut pas être vide!");
                        return;
                    }
                    state.world = input.trim();
                    state.stage = SetupStage.AWAITING_X;
                    player.sendMessage("§6[Teleporter] §fEntrez la coordonnée X:");
                    break;

                case AWAITING_X:
                    state.x = Double.parseDouble(input.trim());
                    state.stage = SetupStage.AWAITING_Y;
                    player.sendMessage("§6[Teleporter] §fEntrez la coordonnée Y:");
                    break;

                case AWAITING_Y:
                    state.y = Double.parseDouble(input.trim());
                    state.stage = SetupStage.AWAITING_Z;
                    player.sendMessage("§6[Teleporter] §fEntrez la coordonnée Z:");
                    break;

                case AWAITING_Z:
                    state.z = Double.parseDouble(input.trim());
                    state.stage = SetupStage.AWAITING_YAW;
                    player.sendMessage("§6[Teleporter] §fEntrez le regard YAW (0):");
                    break;

                case AWAITING_YAW:
                    state.yaw = (float) Double.parseDouble(input.trim());
                    state.stage = SetupStage.AWAITING_PITCH;
                    player.sendMessage("§6[Teleporter] §fEntrez le regard PITCH (0):");
                    break;

                case AWAITING_PITCH:
                    state.pitch = (float) Double.parseDouble(input.trim());
                    state.stage = SetupStage.COMPLETED;
                    player.sendMessage("§a[Teleporter] Configuration terminée! Création du bloc...");
                    break;

                default:
                    break;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c[Teleporter] Format invalide! Veuillez entrer un nombre.");
        }
    }

    public PlayerSetupState getSetupState(Player player) {
        return playerStates.get(player.getUniqueId());
    }

    public void finishSetup(Player player) {
        playerStates.remove(player.getUniqueId());
    }

    public boolean isSetupCompleted(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null && state.stage == SetupStage.COMPLETED;
    }

    public String getTeleporterName(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.teleporterName : null;
    }

    public String getItemname(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.itemname : null;
    }

    public String getDestinationWorld(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.world : null;
    }

    public double getDestinationX(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.x : 0;
    }

    public double getDestinationY(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.y : 64;
    }

    public double getDestinationZ(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.z : 0;
    }

    public float getDestinationYaw(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.yaw : 0;
    }

    public float getDestinationPitch(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSetupState state = playerStates.get(uuid);
        return state != null ? state.pitch : 0;
    }
}

