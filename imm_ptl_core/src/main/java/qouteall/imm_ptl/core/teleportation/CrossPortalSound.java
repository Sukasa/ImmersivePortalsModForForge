package qouteall.imm_ptl.core.teleportation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import javax.annotation.Nullable;
import java.util.Comparator;

@OnlyIn(Dist.CLIENT)
public class CrossPortalSound {
    public static final float VOLUME_RADIUS_MULT = 16f;
    public static final float MIN_SOUND_RADIUS = 16f;
    
    public static boolean isPlayerWorld(ClientLevel world) {
        return world.dimension() == RenderStates.originalPlayerDimension;
    }
    
    @Nullable
    public static SimpleSoundInstance createCrossPortalSound(
        ClientLevel soundWorld,
        SoundEvent sound,
        SoundSource category,
        Vec3 soundPos,
        float soundVol,
        float soundPitch
    ) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player == null) {
            return null;
        }
        
        soundWorld.getProfiler().push("cross_portal_sound");
        
        double soundRadius = Math.min(64, Math.max(VOLUME_RADIUS_MULT * soundVol, MIN_SOUND_RADIUS));
        Vec3 playerCameraPos = RenderStates.originalPlayerPos.add(
            McHelper.getEyeOffset(client.player)
        );
        
        // find portals in range of the sound
        SimpleSoundInstance result = IPMcHelper.getNearbyPortalList(
            soundWorld, soundPos, soundRadius,
            portal -> portal.getDestDim() == RenderStates.originalPlayerDimension &&
                isPlayerInRange(portal, soundPos, soundRadius, playerCameraPos)
        ).stream().min(
            // use portal that is closest to the sound
            Comparator.comparingDouble(portal -> getPortalDistance(portal, soundPos))
        ).map(
            portal -> {
                // set sound position to the point the sound would exit the portal
                Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
                Vec3 soundExitPortalPoint = portal.transformPoint(soundEnterPortalPoint);
                
                // reduce volume based on distance from the sound source to the portal entry point
                float volumeToEnterPortal =
                    (float) soundEnterPortalPoint.distanceTo(soundPos) / VOLUME_RADIUS_MULT;
                
                float volumeMultiplier =
                    Math.max(0, 1 - (float) (playerCameraPos.distanceTo(soundExitPortalPoint) / VOLUME_RADIUS_MULT));
                
                float volumeAtPortal = Math.max(0, soundVol - volumeToEnterPortal) * volumeMultiplier;
                
                return new SimpleSoundInstance(
                    sound,
                    category,
                    volumeAtPortal,
                    soundPitch,
                    soundExitPortalPoint.x(),
                    soundExitPortalPoint.y(),
                    soundExitPortalPoint.z()
                );
            }
        ).orElse(null);
        
        soundWorld.getProfiler().pop();
        
        return result;
    }
    
    private static boolean isPlayerInRange(
        Portal portal, Vec3 soundPos, double soundRadius, Vec3 playerCameraPos
    ) {
        Vec3 soundExitPoint = portal.transformPoint(portal.getNearestPointInPortal(soundPos));
        return soundExitPoint.closerThan(playerCameraPos, soundRadius);
    }
    
    private static double getPortalDistance(Portal portal, Vec3 soundPos) {
        Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
        return soundEnterPortalPoint.distanceToSqr(soundPos);
    }
}
