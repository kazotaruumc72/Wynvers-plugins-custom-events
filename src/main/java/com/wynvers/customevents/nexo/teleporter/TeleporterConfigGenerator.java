package com.wynvers.customevents.nexo.teleporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a Nexo item configuration YAML for teleporters and writes it to disk.
 */
public class TeleporterConfigGenerator {

    private final File nexoItemsDir;

    public TeleporterConfigGenerator(File nexoItemsDir) {
        this.nexoItemsDir = nexoItemsDir;
    }

    /**
     * Generates and writes a teleporter item configuration to the Nexo items directory.
     */
    public boolean generateTeleporterConfig(String teleporterId, String itemname, String destinationWorld,
                                           double x, double y, double z, float yaw, float pitch) {
        try {
            File itemsFile = new File(nexoItemsDir, "teleporter_items.yml");
            String config = generateTeleporterYaml(teleporterId, itemname, destinationWorld, x, y, z, yaw, pitch);

            if (!itemsFile.exists()) {
                itemsFile.createNewFile();
                Files.write(itemsFile.toPath(), generateFileHeader().getBytes());
            }

            Files.write(itemsFile.toPath(), ("\n" + config).getBytes(), StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String generateFileHeader() {
        return "# Auto-generated teleporter items\n" +
               "# These items are dynamically created by the Teleporter mechanic\n";
    }

    private String generateTeleporterYaml(String teleporterId, String itemname, String destinationWorld,
                                         double x, double y, double z, float yaw, float pitch) {
        List<String> lines = new ArrayList<>();

        lines.add(teleporterId + ":");
        lines.add("  itemname: \"" + itemname.replace("\"", "\\\"") + "\"");
        lines.add("  material: PAPER");
        lines.add("  Mechanics:");
        lines.add("    custom_block:");
        lines.add("      type: NOTEBLOCK");
        lines.add("      custom_variation: \"476\"");
        lines.add("    teleporter:");
        lines.add("      world: \"" + destinationWorld + "\"");
        lines.add("      x: \"" + x + "\"");
        lines.add("      y: \"" + y + "\"");
        lines.add("      z: \"" + z + "\"");
        lines.add("      yaw: \"" + yaw + "\"");
        lines.add("      pitch: \"" + pitch + "\"");
        lines.add("  Pack:");
        lines.add("    parent_model: block/cube_all");
        lines.add("    texture: fischvogel:fv_bettersnow/snow");

        return String.join("\n", lines);
    }
}


