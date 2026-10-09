package com.megawalls.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;

/** The central battlefield enclosed by the configured walls; independent of height. */
public final class DeathmatchZone {
    private DeathmatchZone() {}

    public record Wall(int x1, int z1, int x2, int z2) {}
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        // Include the former wall blocks, with the same block-coordinate convention as the arena.
        public boolean contains(double x, double z) {
            return x >= minX && x < (double)maxX + 1 && z >= minZ && z < (double)maxZ + 1;
        }
    }

    public static Bounds enclosedBounds(List<Wall> walls, double centerX, double centerZ) {
        if (walls.size() != 4) return null;
        var vertical = new ArrayList<Wall>();
        var horizontal = new ArrayList<Wall>();
        for (var wall : walls) {
            if (wall.x1 == wall.x2 && wall.z1 != wall.z2) vertical.add(wall);
            else if (wall.z1 == wall.z2 && wall.x1 != wall.x2) horizontal.add(wall);
            else return null;
        }
        if (vertical.size() != 2 || horizontal.size() != 2) return null;
        int minX = Math.min(vertical.get(0).x1, vertical.get(1).x1);
        int maxX = Math.max(vertical.get(0).x1, vertical.get(1).x1);
        int minZ = Math.min(horizontal.get(0).z1, horizontal.get(1).z1);
        int maxZ = Math.max(horizontal.get(0).z1, horizontal.get(1).z1);
        if (minX >= maxX || minZ >= maxZ || centerX <= minX || centerX >= maxX
                || centerZ <= minZ || centerZ >= maxZ) return null;
        for (var wall : vertical)
            if (Math.min(wall.z1, wall.z2) > minZ || Math.max(wall.z1, wall.z2) < maxZ) return null;
        for (var wall : horizontal)
            if (Math.min(wall.x1, wall.x2) > minX || Math.max(wall.x1, wall.x2) < maxX) return null;
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    public static boolean contains(List<Wall> walls, double centerX, double centerZ,
                                   int radius, double x, double z) {
        var bounds = enclosedBounds(walls, centerX, centerZ);
        if (bounds != null) return bounds.contains(x, z);
        // Older two-team maps do not enclose a rectangle. Retain their configured radius.
        double dx = x - centerX, dz = z - centerZ, safeRadius = Math.max(1, radius);
        return dx * dx + dz * dz <= safeRadius * safeRadius;
    }

    public static boolean contains(ServerPlayerEntity player) {
        var server = player.getServer();
        if (player.getServerWorld() != server.getOverworld()) return false;
        var walls = new ArrayList<Wall>();
        if (MapConfig.walls(server) == 4) {
            for (int i = 0; i < 4; i++) {
                String first = "wall" + (i * 2 + 1), second = "wall" + (i * 2 + 2);
                if (MapConfig.get(server, first + "_set") != 1 || MapConfig.get(server, second + "_set") != 1) {
                    walls.clear(); break;
                }
                var a = MapConfig.point(server, first); var b = MapConfig.point(server, second);
                walls.add(new Wall(a.getX(), a.getZ(), b.getX(), b.getZ()));
            }
        }
        var center = MapConfig.point(server, "center");
        return contains(walls, center.getX() + .5, center.getZ() + .5,
                MapConfig.get(server, "radius"), player.getX(), player.getZ());
    }
}
