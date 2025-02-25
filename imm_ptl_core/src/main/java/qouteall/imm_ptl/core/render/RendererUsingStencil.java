package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalLike;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

//NOTE do not use glDisable(GL_DEPTH_TEST), use GlStateManager.disableDepthTest() instead
//because GlStateManager will cache its state. Do not make its cache not synchronized
public class RendererUsingStencil extends PortalRenderer {
    
    
    @Override
    public boolean replaceFrameBufferClearing() {
        boolean skipClearing = PortalRendering.isRendering();
        if (skipClearing) {
            boolean isSkyTransparent = PortalRendering.getRenderingPortal().isFuseView();
            
            if (!isSkyTransparent) {
                RenderSystem.depthMask(false);
                MyRenderHelper.renderScreenTriangle(FogRendererContext.getCurrentFogColor.get());
                RenderSystem.depthMask(true);
            }
        }
        return skipClearing;
    }
    
    @Override
    public void onBeforeTranslucentRendering(PoseStack matrixStack) {
        doPortalRendering(matrixStack);
    }
    
    protected void doPortalRendering(PoseStack matrixStack) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        
        client.getProfiler().popPush("render_portal_total");
        renderPortals(matrixStack);
        if (PortalRendering.isRendering()) {
            setStencilStateForWorldRendering();
        }
        else {
            // TODO why not do it in finishRendering()?
            myFinishRendering();
        }
    }
    
    protected void renderPortals(PoseStack matrixStack) {
        List<PortalLike> portalsToRender = getPortalsToRender(matrixStack);
        
        for (PortalLike portal : portalsToRender) {
            doRenderPortal(portal, matrixStack);
        }
    }
    
    @Override
    public void onAfterTranslucentRendering(PoseStack matrixStack) {
    
    }
    
    @Override
    public void onHandRenderingEnded(PoseStack matrixStack) {
        //nothing
    }
    
    @Override
    public void prepareRendering() {
        if (!IPPortingLibCompat.getIsStencilEnabled(client.getMainRenderTarget())) {
            IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), true);
            
            if (Minecraft.useShaderTransparency()) {
//                client.worldRenderer.reload();
            }
        }
        
        client.getMainRenderTarget().bindWrite(false);
        
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        
        GlStateManager._enableDepthTest();
        GL11.glEnable(GL_STENCIL_TEST);
        
    }
    
    @Override
    public void finishRendering() {
        //nothing
    }
    
    private void myFinishRendering() {
        GL11.glStencilFunc(GL_ALWAYS, 2333, 0xFF);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        
        GL11.glDisable(GL_STENCIL_TEST);
        GlStateManager._enableDepthTest();
    }
    
    protected void doRenderPortal(
        PortalLike portal,
        PoseStack matrixStack
    ) {
        if (shouldSkipRenderingInsideFuseViewPortal(portal)) {
            return;
        }
        
        int outerPortalStencilValue = PortalRendering.getPortalLayer();
        
        client.getProfiler().push("render_view_area");
        
        boolean anySamplePassed = PortalRenderInfo.renderAndDecideVisibility(portal, () -> {
            renderPortalViewAreaToStencil(portal, matrixStack);
        });
        
        client.getProfiler().pop();
        
        if (!anySamplePassed) {
            setStencilStateForWorldRendering();
            return;
        }
        
        PortalRendering.pushPortalLayer(portal);
        
        int thisPortalStencilValue = outerPortalStencilValue + 1;
        
        if (!portal.isFuseView()) {
            client.getProfiler().push("clear_depth_of_view_area");
            clearDepthOfThePortalViewArea(portal);
            client.getProfiler().pop();
        }
        
        setStencilStateForWorldRendering();
        
        renderPortalContent(portal);
        
        if (!portal.isFuseView()) {
            restoreDepthOfPortalViewArea(portal, matrixStack);
        }
        
        clampStencilValue(outerPortalStencilValue);
        
        PortalRendering.popPortalLayer();
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        //nothing
    }
    
    private void renderPortalViewAreaToStencil(
        PortalLike portal, PoseStack matrixStack
    ) {
        int outerPortalStencilValue = PortalRendering.getPortalLayer();
        
        //is the mask here different from the mask of glStencilMask?
        GL11.glStencilFunc(GL_EQUAL, outerPortalStencilValue, 0xFF);
        
        //if stencil and depth test pass, the data in stencil buffer will increase by 1
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
        //NOTE about GL_INCR:
        //if multiple triangles occupy the same pixel and passed stencil and depth tests,
        //its stencil value will still increase by one
        
        GL11.glStencilMask(0xFF);
        
        // update it before pushing
        FrontClipping.updateInnerClipping(matrixStack);
        
        ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO,
            matrixStack.last().pose(),
            RenderSystem.getProjectionMatrix(),
            true, true,
            true);
    }
    
    private void clearDepthOfThePortalViewArea(
        PortalLike portal
    ) {
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        
        setStencilStateForWorldRendering();
        
        //do not manipulate color buffer
        GL11.glColorMask(false, false, false, false);
        
        //save the state
        int originalDepthFunc = GL11.glGetInteger(GL_DEPTH_FUNC);
        
        //always passes depth test
        GL11.glDepthFunc(GL_ALWAYS);
        
        //the pixel's depth will be 1, which is the furthest
        GL11.glDepthRange(1, 1);
        
        MyRenderHelper.renderScreenTriangle();
        
        //retrieve the state
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(originalDepthFunc);
        GL11.glDepthRange(0, 1);
    }
    
    protected void restoreDepthOfPortalViewArea(
        PortalLike portal, PoseStack matrixStack
    ) {
        setStencilStateForWorldRendering();
        
        int originalDepthFunc = GL11.glGetInteger(GL_DEPTH_FUNC);
        
        GL11.glDepthFunc(GL_ALWAYS);
        
        FrontClipping.disableClipping();
        
        ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO,
            matrixStack.last().pose(),
            RenderSystem.getProjectionMatrix(),
            false,
            false,
            true
        );
        
        GL11.glDepthFunc(originalDepthFunc);
    }
    
    public static void clampStencilValue(
        int maximumValue
    ) {
        GlStateManager._depthMask(true);
        
        //NOTE GL_GREATER means ref > stencil
        //GL_LESS means ref < stencil
        
        //pass if the stencil value is greater than the maximum value
        GL11.glStencilFunc(GL_LESS, maximumValue, 0xFF);
        
        //if stencil test passed, encode the stencil value
        GL11.glStencilOp(GL_KEEP, GL_REPLACE, GL_REPLACE);
        
        //do not manipulate the depth buffer
        GL11.glDepthMask(false);
        
        //do not manipulate the color buffer
        GL11.glColorMask(false, false, false, false);
        
        GlStateManager._disableDepthTest();
        
        MyRenderHelper.renderScreenTriangle();
        
        GL11.glDepthMask(true);
        
        GL11.glColorMask(true, true, true, true);
        
        GlStateManager._enableDepthTest();
    }
    
    private void setStencilStateForWorldRendering() {
        int thisPortalStencilValue = PortalRendering.getPortalLayer();
        
        //draw content in the mask
        GL11.glStencilFunc(GL_EQUAL, thisPortalStencilValue, 0xFF);
        
        //do not manipulate stencil buffer now
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }
    
    public static boolean shouldSkipRenderingInsideFuseViewPortal(PortalLike portal) {
        if (!PortalRendering.isRendering()) {
            return false;
        }
        
        PortalLike renderingPortal = PortalRendering.getRenderingPortal();
        
        if (!renderingPortal.isFuseView()) {
            return false;
        }
        
        Vec3 cameraPos = CHelper.getCurrentCameraPos();
        
        Vec3 transformedCameraPos = portal.transformPoint(renderingPortal.transformPoint(cameraPos));
        
        // roughly test whether they are reverse portals
        return cameraPos.distanceToSqr(transformedCameraPos) < 0.1;
    }
}
