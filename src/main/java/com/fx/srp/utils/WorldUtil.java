package com.fx.srp.utils;

import com.fx.srp.SpeedRunPlus;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseNetherPortals.MultiverseNetherPortals;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

public class WorldUtil {

    public static Location generateWorlds(String overworld, String nether, String end, MVWorldManager worldManager, MultiverseNetherPortals portals) {
        worldManager.addWorld(overworld, World.Environment.NORMAL, null, WorldType.NORMAL, Boolean.TRUE, null);
        String seed = String.valueOf(worldManager.getMVWorld(overworld).getSeed());
        worldManager.addWorld(nether, World.Environment.NETHER, seed, WorldType.NORMAL, Boolean.TRUE, null);
        worldManager.addWorld(end, World.Environment.THE_END, seed, WorldType.NORMAL, Boolean.TRUE, null);
        worldManager.getMVWorld(nether).setRespawnToWorld(overworld);
        worldManager.getMVWorld(end).setRespawnToWorld(overworld);

        // Set the spawn in the overworld
        Location location = worldManager.getMVWorld(overworld).getSpawnLocation();
        worldManager.getMVWorld(overworld).setSpawnLocation(location);

        // Link overworld, nether and end
        portals.addWorldLink(overworld, nether, PortalType.NETHER);
        portals.addWorldLink(nether, overworld, PortalType.NETHER);
        portals.addWorldLink(overworld, end, PortalType.ENDER);
        portals.addWorldLink(end, overworld, PortalType.ENDER);

        return location;
    }

    public static void deleteWorlds(SpeedRunPlus plugin, MVWorldManager worldManager, Player player) {
        UUID uuid = player.getUniqueId();
        worldManager.deleteWorld(plugin.getSRP_OVERWORLD_PREFIX() + uuid);
        worldManager.deleteWorld(plugin.getSRP_NETHER_PREFIX() + uuid);
        worldManager.deleteWorld(plugin.getSRP_END_PREFIX() + uuid);
    }
}

