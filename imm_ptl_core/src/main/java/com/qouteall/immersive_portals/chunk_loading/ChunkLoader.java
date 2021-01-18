package com.qouteall.immersive_portals.chunk_loading;

import com.qouteall.immersive_portals.McHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Objects;

//the players and portals are chunk loaders
public class ChunkLoader {
    public DimensionalChunkPos center;
    public int radius;
    public boolean isDirectLoader = false;
    
    public ChunkLoader(DimensionalChunkPos center, int radius) {
        this(center, radius, false);
    }
    
    public ChunkLoader(DimensionalChunkPos center, int radius, boolean isDirectLoader) {
        this.center = center;
        this.radius = radius;
        this.isDirectLoader = isDirectLoader;
    }
    
    public int getLoadedChunkNum() {
        int[] numBox = {0};
        foreachChunkPos((dim, x, z, dist) -> {
            WorldChunk chunk = McHelper.getServerChunkIfPresent(dim, x, z);
            if (chunk != null) {
                numBox[0] += 1;
            }
        });
        return numBox[0];
    }
    
    public int getChunkNum() {
        return (this.radius * 2 + 1) * (this.radius * 2 + 1);
    }
    
    public void foreachChunkPos(ChunkVisibilityManager.ChunkPosConsumer func) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                func.consume(
                    center.dimension,
                    center.x + dx,
                    center.z + dz,
                    Math.max(Math.abs(dx), Math.abs(dz))
                );
            }
        }
    }
    
    public LenientChunkRegion createChunkRegion() {
        ServerWorld world = McHelper.getServer().getWorld(center.dimension);
        
        return LenientChunkRegion.createLenientChunkRegion(center, radius, world);
    }
    
    @Override
    public String toString() {
        return "{" +
            "center=" + center +
            ", radius=" + radius +
            '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkLoader that = (ChunkLoader) o;
        return radius == that.radius &&
            center.equals(that.center);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(center, radius);
    }
}
