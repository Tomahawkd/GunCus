package de.cas_ual_ty.guncus.client;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import de.cas_ual_ty.guncus.GunCus;
import de.cas_ual_ty.guncus.IProxy;
import de.cas_ual_ty.guncus.client.gui.GuiContainerBulletMaker;
import de.cas_ual_ty.guncus.client.gui.GuiContainerGunMaker;
import de.cas_ual_ty.guncus.client.gui.GuiContainerGunTable;
import de.cas_ual_ty.guncus.entity.EntityBullet;
import de.cas_ual_ty.guncus.item.ItemAttachment;
import de.cas_ual_ty.guncus.item.ItemGun;
import de.cas_ual_ty.guncus.item.attachments.Accessory;
import de.cas_ual_ty.guncus.item.attachments.Accessory.Laser;
import de.cas_ual_ty.guncus.item.attachments.EnumAttachmentType;
import de.cas_ual_ty.guncus.item.attachments.Optic;
import de.cas_ual_ty.guncus.itemgroup.ItemGroupShuffle;
import de.cas_ual_ty.guncus.network.MessageShoot;
import de.cas_ual_ty.guncus.registries.GunCusContainerTypes;
import de.cas_ual_ty.guncus.util.GunCusUtility;
import net.minecraft.block.Blocks;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceContext.BlockMode;
import net.minecraft.util.math.RayTraceContext.FluidMode;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.network.NetworkEvent.Context;

public class ProxyClient implements IProxy
{
    public static final Supplier<KeyBinding> BUTTON_AIM = () -> ProxyClient.getMC().gameSettings.keyBindUseItem;
    public static final Supplier<KeyBinding> BUTTON_SHOOT = () -> ProxyClient.getMC().gameSettings.keyBindAttack;
    public static final Supplier<Boolean> BUTTON_AIM_DOWN = () -> ProxyClient.BUTTON_AIM.get().isKeyDown();
    public static final Supplier<Boolean> BUTTON_SHOOT_DOWN = () -> ProxyClient.BUTTON_SHOOT.get().isKeyDown();
    
    public static final ResourceLocation HITMARKER_TEXTURE = new ResourceLocation(GunCus.MOD_ID, "textures/gui/hitmarker.png");
    
    public static final int HITMARKER_RESET = 4;
    
    // --- RENDER STUFF - COPIED FROM RenderType CLASS ---
    
    public static final RenderState.TransparencyState LASER_TRANSPARENCY = new RenderState.TransparencyState("laser_transparency", () ->
    {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }, () ->
    {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });
    
    public static final RenderState.ShadeModelState SHADE_ENABLED = new RenderState.ShadeModelState(true);
    public static final RenderState.WriteMaskState COLOR_WRITE = new RenderState.WriteMaskState(true, false);
    
    // Lightning Copied
    public static final RenderType LASER_POINT = RenderType.makeType("laser_point", DefaultVertexFormats.POSITION_COLOR, GL11.GL_QUADS, 256, false, true, RenderType.State.getBuilder().writeMask(ProxyClient.COLOR_WRITE).transparency(ProxyClient.LASER_TRANSPARENCY).shadeModel(ProxyClient.SHADE_ENABLED).build(false));
    public static final RenderType LASER_BEAM = RenderType.makeType("laser_beam", DefaultVertexFormats.POSITION_COLOR, GL11.GL_LINES, 256, false, true, RenderType.State.getBuilder().line(new RenderState.LineState(OptionalDouble.empty())).writeMask(ProxyClient.COLOR_WRITE).transparency(ProxyClient.LASER_TRANSPARENCY).shadeModel(ProxyClient.SHADE_ENABLED).build(false));
    
    // --- RENDER STUFF END ---
    
    private static int hitmarkerTick = 0;
    private static int shootTime[] = new int[GunCusUtility.HANDS.length];
    private static int inaccuracyTime = 0;
    private static int prevSelectedMain = -1;
    
    @Override
    public void registerModEventListeners(IEventBus bus)
    {
        bus.addListener(this::clientSetup);
        bus.addListener(this::modelBake);
        bus.addListener(this::modelRegistry);
    }
    
    @Override
    public void registerForgeEventListeners(IEventBus bus)
    {
        bus.addListener(this::clientTick);
        bus.addListener(this::fovUpdate);
        bus.addListener(this::renderGameOverlayPre);
        bus.addListener(this::renderWorldLast);
    }
    
    @Override
    public void init()
    {
        ScreenManager.registerFactory(GunCusContainerTypes.GUN_TABLE, GuiContainerGunTable::new);
        ScreenManager.registerFactory(GunCusContainerTypes.GUN_MAKER, GuiContainerGunMaker::new);
        ScreenManager.registerFactory(GunCusContainerTypes.BULLET_MAKER, GuiContainerBulletMaker::new);
    }
    
    @Override
    public void addHitmarker(PlayerEntity player)
    {
        ProxyClient.hitmarkerTick = ProxyClient.HITMARKER_RESET;
    }
    
    @Override
    public PlayerEntity getPlayerFromContext(@Nullable Context context)
    {
        return ProxyClient.getClientPlayer();
    }
    
    @Override
    public void shot(ItemStack itemStack, ItemGun gun, PlayerEntity player, Hand hand)
    {
        int fireRate = gun.calcCurrentFireRate(gun.getCurrentAttachments(itemStack));
        ProxyClient.shootTime[hand == Hand.MAIN_HAND ? 0 : 1] = fireRate;
        ProxyClient.inaccuracyTime = Math.min(15, ProxyClient.inaccuracyTime + 2 + fireRate);
    }
    
    public void clientSetup(FMLClientSetupEvent event)
    {
        // TODO
    }
    
    public void modelBake(ModelBakeEvent event)
    {
        ModelResourceLocation mrl;
        IBakedModel main;
        
        Optic optic;
        
        for(ItemAttachment attachment : ItemAttachment.ATTACHMENTS_LIST)
        {
            if(attachment.getType() == EnumAttachmentType.OPTIC)
            {
                optic = (Optic)attachment;
                
                if(optic != null && optic.canAim())
                {
                    mrl = new ModelResourceLocation(optic.getRegistryName().toString(), "inventory");
                    main = event.getModelRegistry().get(mrl);
                    event.getModelRegistry().put(mrl, new BakedModelOptic(main));
                }
            }
        }
        
        int i;
        int j;
        ItemAttachment attachment;
        
        IBakedModel[][] models; //These are the ItemAttachment models which will be passed onto the gun model for use
        
        for(ItemGun gun : ItemGun.GUNS_LIST) //Cycle through all guns
        {
            models = new IBakedModel[EnumAttachmentType.LENGTH][];
            
            for(EnumAttachmentType type : EnumAttachmentType.VALUES) //This represents the layers
            {
                i = type.getSlot();
                
                models[i] = new IBakedModel[gun.getAmmountForSlot(type)];
                
                for(j = 0; j < models[i].length; ++j) //Ammount of ItemAttachments for each layer
                {
                    attachment = gun.getAttachment(type, j);
                    
                    if(attachment != null && attachment.shouldLoadModel()) //Make sure its not null-attachment and the model is needed
                    {
                        models[i][j] = event.getModelRegistry().get(new ModelResourceLocation(gun.getRegistryName().toString() + "/" + attachment.getRegistryName().getPath(), "inventory")); //Add ItemAttachment model to the array
                    }
                }
            }
            
            mrl = new ModelResourceLocation(gun.getRegistryName().toString(), "inventory"); //This is the MRL of the main item (gun)
            
            main = event.getModelRegistry().get(mrl); //Get the model of the gun
            
            MatrixStack stack = new MatrixStack();
            event.getModelRegistry().get(new ModelResourceLocation(gun.getRegistryName().toString() + "/aim", "inventory")).handlePerspective(TransformType.FIRST_PERSON_RIGHT_HAND, stack);
            Matrix4f aimMatrix = stack.getLast().getMatrix();
            
            event.getModelRegistry().put(mrl, new BakedModelGun(main, models, aimMatrix)); //Replace model of the gun with custom IBakedModel and pass all the ItemAttachment models to it
        }
    }
    
    public void modelRegistry(ModelRegistryEvent event)
    {
        int i;
        ItemAttachment attachment;
        
        for(ItemGun gun : ItemGun.GUNS_LIST)
        {
            ModelLoader.addSpecialModel(new ModelResourceLocation(gun.getRegistryName().toString() + "/gun", "inventory"));
            
            for(EnumAttachmentType type : EnumAttachmentType.VALUES) //All layers
            {
                for(i = 0; i < gun.getAmmountForSlot(type); ++i) //All attachments per layer
                {
                    attachment = gun.getAttachment(type, i);
                    
                    if(attachment != null && attachment.shouldLoadModel()) //null-attachment exists, as well as some which are not visible
                    {
                        ModelLoader.addSpecialModel(new ModelResourceLocation(gun.getRegistryName().toString() + "/" + attachment.getRegistryName().getPath(), "inventory")); //Add MRL to the list
                    }
                }
            }
            
            ModelLoader.addSpecialModel(new ModelResourceLocation(gun.getRegistryName().toString() + "/aim", "inventory"));
        }
    }
    
    public void clientTick(ClientTickEvent event)
    {
        if(event.phase == Phase.START)
        {
            PlayerEntity entityPlayer = ProxyClient.getClientPlayer();
            
            if(entityPlayer == null)
            {
                return;
            }
            
            ItemStack itemStack;
            ItemGun gun;
            
            int i = 0;
            for(Hand hand : GunCusUtility.HANDS)
            {
                if(ProxyClient.shootTime[i] > 0)
                {
                    --ProxyClient.shootTime[i];
                }
                
                itemStack = entityPlayer.getHeldItem(hand);
                
                if(itemStack.getItem() instanceof ItemGun)
                {
                    gun = (ItemGun)itemStack.getItem();
                    
                    if(gun.getNBTIsReloading(itemStack))
                    {
                        ProxyClient.shootTime[i] = 1;
                    }
                    else if(hand == Hand.MAIN_HAND)
                    {
                        if(entityPlayer.inventory.currentItem != ProxyClient.prevSelectedMain)
                        {
                            ProxyClient.shootTime[i] += gun.calcCurrentSwitchTime(gun.getCurrentAttachments(itemStack));
                        }
                    }
                }
                
                ++i;
            }
            
            ProxyClient.prevSelectedMain = entityPlayer.inventory.currentItem;
            
            if(ProxyClient.inaccuracyTime > 0)
            {
                --ProxyClient.inaccuracyTime;
            }
            
            // ---
            
            if(ProxyClient.BUTTON_SHOOT_DOWN.get() && (entityPlayer.getHeldItemMainhand().getItem() instanceof ItemGun || entityPlayer.getHeldItemOffhand().getItem() instanceof ItemGun))
            {
                boolean aiming = false;
                
                if(ProxyClient.BUTTON_AIM_DOWN.get() && !entityPlayer.isSprinting())
                {
                    if(entityPlayer.getHeldItemMainhand().getItem() instanceof ItemGun && entityPlayer.getHeldItemOffhand().isEmpty())
                    {
                        itemStack = entityPlayer.getHeldItemMainhand();
                        gun = (ItemGun)itemStack.getItem();
                        
                        if(gun.getNBTCanAimGun(itemStack))
                        {
                            Optic optic = gun.<Optic>getAttachmentCalled(itemStack, EnumAttachmentType.OPTIC);
                            aiming = optic.canAim();
                        }
                    }
                }
                
                i = 0;
                int handsInt = 0;
                
                for(i = 0; i < ProxyClient.shootTime.length; ++i)
                {
                    if(entityPlayer.getHeldItem(GunCusUtility.HANDS[i]).getItem() instanceof ItemGun && ProxyClient.shootTime[i] <= 0)
                    {
                        handsInt += i + 1;
                    }
                }
                
                if(handsInt > 0)
                {
                    GunCus.channel.sendToServer(new MessageShoot(aiming, ProxyClient.inaccuracyTime, handsInt));
                    ItemGun.tryShoot(entityPlayer, aiming, ProxyClient.inaccuracyTime, GunCusUtility.intToHands(handsInt));
                }
            }
            
            // ---
            
            for(ItemGroupShuffle group : ItemGroupShuffle.GROUPS_LIST)
            {
                group.tick();
            }
        }
        else if(event.phase == Phase.END)
        {
            if(ProxyClient.hitmarkerTick > 0)
            {
                --ProxyClient.hitmarkerTick;
            }
        }
    }
    
    public void fovUpdate(FOVUpdateEvent event)
    {
        PlayerEntity entityPlayer = ProxyClient.getClientPlayer();
        
        if(entityPlayer != null && ProxyClient.BUTTON_AIM_DOWN.get())
        {
            if(!entityPlayer.isSprinting())
            {
                Optic optic = null;
                float modifier = 1F;
                float extra = 0F;
                
                if(entityPlayer.getHeldItemMainhand().getItem() instanceof ItemGun || entityPlayer.getHeldItemOffhand().getItem() instanceof ItemGun)
                {
                    if(entityPlayer.getHeldItemOffhand().isEmpty())
                    {
                        ItemStack itemStack = entityPlayer.getHeldItemMainhand();
                        ItemGun gun = (ItemGun)itemStack.getItem();
                        
                        if(gun.getNBTCanAimGun(itemStack))
                        {
                            optic = gun.<Optic>getAttachmentCalled(itemStack, EnumAttachmentType.OPTIC);
                        }
                        
                        if(optic != null && gun.isNBTAccessoryTurnedOn(itemStack) && !gun.getAttachment(itemStack, EnumAttachmentType.ACCESSORY).isDefault())
                        {
                            Accessory accessory = gun.<Accessory>getAttachmentCalled(itemStack, EnumAttachmentType.ACCESSORY);
                            
                            if(optic.isCompatibleWithMagnifiers())
                            {
                                modifier = accessory.getZoomModifier();
                            }
                            
                            if(optic.isCompatibleWithExtraZoom())
                            {
                                extra = accessory.getExtraZoom();
                            }
                        }
                    }
                }
                else if(entityPlayer.getHeldItemMainhand().getItem() instanceof Optic)
                {
                    ItemStack itemStack = entityPlayer.getHeldItemMainhand();
                    optic = (Optic)itemStack.getItem();
                }
                
                if(optic != null && optic.canAim())
                {
                    event.setNewfov(ProxyClient.calculateFov(optic.getZoom() * modifier + 0.1F + extra, event.getFov()));
                }
            }
        }
    }
    
    public static float calculateFov(float zoom, float fov)
    {
        return (float)Math.atan(Math.tan(fov) / zoom);
    }
    
    public void renderGameOverlayPre(RenderGameOverlayEvent.Pre event)
    {
        PlayerEntity entityPlayer = ProxyClient.getClientPlayer();
        ItemStack itemStack;
        ItemGun gun;
        
        if(event.getType() == ElementType.CROSSHAIRS && entityPlayer != null)
        {
            Optic optic = null;
            
            if(entityPlayer.getHeldItemMainhand().getItem() instanceof ItemGun || entityPlayer.getHeldItemOffhand().getItem() instanceof ItemGun)
            {
                if(entityPlayer.getHeldItemOffhand().isEmpty())
                {
                    itemStack = entityPlayer.getHeldItemMainhand();
                    gun = (ItemGun)itemStack.getItem();
                    
                    if(gun.getNBTCanAimGun(itemStack))
                    {
                        optic = gun.<Optic>getAttachmentCalled(itemStack, EnumAttachmentType.OPTIC);
                    }
                }
                
                event.setCanceled(true);
            }
            else if(entityPlayer.getHeldItemMainhand().getItem() instanceof Optic)
            {
                optic = (Optic)entityPlayer.getHeldItemMainhand().getItem();
            }
            
            if(optic != null && optic.canAim() && !entityPlayer.isSprinting() && ProxyClient.BUTTON_AIM_DOWN.get())
            {
                ProxyClient.drawSight(optic, event.getWindow());
                
                if(!event.isCanceled())
                {
                    event.setCanceled(true);
                }
            }
            
            // ---
            
            Accessory accessory;
            Vec3d start = new Vec3d(entityPlayer.getPosX(), entityPlayer.getPosY() + entityPlayer.getEyeHeight(), entityPlayer.getPosZ());
            Vec3d end;
            Vec3d hit;
            
            for(Hand hand : GunCusUtility.HANDS)
            {
                itemStack = entityPlayer.getHeldItem(hand);
                
                if(itemStack.getItem() instanceof ItemGun)
                {
                    gun = (ItemGun)itemStack.getItem();
                    accessory = gun.getAttachmentCalled(itemStack, EnumAttachmentType.ACCESSORY);
                }
                else if(itemStack.getItem() instanceof Accessory)
                {
                    accessory = (Accessory)itemStack.getItem();
                }
                else
                {
                    accessory = (Accessory)EnumAttachmentType.ACCESSORY.getDefault();
                }
                
                if(accessory.getLaser() != null && accessory.getLaser().isRangeFinder())
                {
                    end = start.add(entityPlayer.getLookVec().normalize().scale(accessory.getLaser().getMaxRange()));
                    hit = ProxyClient.findHit(entityPlayer.world, entityPlayer, start, end);
                    
                    hit = hit.subtract(start);
                    
                    ProxyClient.drawRangeFinder(event.getWindow(), hand, hit.length());
                }
            }
            
            // ---
            
            if(ProxyClient.hitmarkerTick > 0)
            {
                ProxyClient.drawHitmarker(event.getWindow());
            }
        }
    }
    
    public static void drawSight(Optic optic, MainWindow sr)
    {
        ProxyClient.drawDrawFullscreenImage(optic.getOverlay(), 1024, 256, sr);
    }
    
    public static void drawRangeFinder(MainWindow sr, Hand hand, double range)
    {
        ProxyClient.drawRangeFinder(sr, hand, (int)range + "");
    }
    
    public static void drawRangeFinder(MainWindow sr, Hand hand, String text)
    {
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        
        int off = 8;
        FontRenderer font = ProxyClient.getMC().fontRenderer;
        off = hand == Hand.OFF_HAND ? -(font.getStringWidth(text) + 1 + off) : off;
        
        font.drawStringWithShadow(text, sr.getScaledWidth() / 2 + off, sr.getScaledHeight() / 2, 0xFFFFFF);
        
        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
    }
    
    public static void drawHitmarker(MainWindow sr)
    {
        ProxyClient.drawDrawFullscreenImage(ProxyClient.HITMARKER_TEXTURE, 1024, 256, sr);
    }
    
    public static void drawDrawFullscreenImage(ResourceLocation rl, int texWidth, int texHeight, MainWindow sr)
    {
        RenderSystem.pushMatrix();
        
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        RenderSystem.disableAlphaTest();
        
        ProxyClient.getMC().getTextureManager().bindTexture(rl);
        
        double x = sr.getScaledWidth();
        double y = sr.getScaledHeight();
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder b = tessellator.getBuffer();
        
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        
        b.pos(x * 0.5D - 2 * y, y, -90D).tex(0F, 1F).endVertex();
        b.pos(x * 0.5D + 2 * y, y, -90D).tex(1F, 1F).endVertex();
        b.pos(x * 0.5D + 2 * y, 0D, -90D).tex(1F, 0F).endVertex();
        b.pos(x * 0.5D - 2 * y, 0D, -90D).tex(0F, 0F).endVertex();
        
        tessellator.draw();
        
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableAlphaTest();
        
        RenderSystem.popMatrix();
    }
    
    public void renderWorldLast(RenderWorldLastEvent event)
    {
        IRenderTypeBuffer.Impl b = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
        MatrixStack matrixStack = event.getMatrixStack();
        
        matrixStack.push();
        
        ActiveRenderInfo renderInfo = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();
        Vec3d v = renderInfo.getProjectedView();
        v = v.inverse();
        matrixStack.translate(v.x, v.y, v.z);
        
        if(ProxyClient.getMC().getRenderViewEntity() != null)
        {
            World world = ProxyClient.getMC().getRenderViewEntity().world;
            
            for(PlayerEntity player : world.getPlayers())
            {
                ItemStack itemStack;
                ItemGun gun;
                Accessory accessory;
                Laser laser;
                
                Vec3d start;
                Vec3d end;
                
                Vec3d playerPos = player.getEyePosition(event.getPartialTicks());
                Vec3d playerLook = player.getLookVec().normalize();
                Vec3d handOff;
                
                for(PlayerEntity entityPlayer : world.getPlayers())
                {
                    if(entityPlayer.isAlive())
                    {
                        for(Hand hand : GunCusUtility.HANDS)
                        {
                            accessory = null;
                            itemStack = entityPlayer.getHeldItem(hand);
                            
                            if(itemStack.getItem() instanceof ItemGun)
                            {
                                gun = (ItemGun)itemStack.getItem();
                                
                                if(gun.isNBTAccessoryTurnedOn(itemStack))
                                {
                                    accessory = gun.<Accessory>getAttachmentCalled(itemStack, EnumAttachmentType.ACCESSORY);
                                }
                            }
                            else if(itemStack.getItem() instanceof Accessory)
                            {
                                accessory = (Accessory)itemStack.getItem();
                            }
                            
                            if(accessory != null && accessory.getLaser() != null)
                            {
                                laser = accessory.getLaser();
                                
                                handOff = ProxyClient.getOffsetForHand(entityPlayer, hand).add(ProxyClient.getVectorForRotation(entityPlayer.rotationPitch + -345F, entityPlayer.rotationYaw));
                                
                                start = playerPos.add(handOff);
                                end = start.add(playerLook.scale(laser.getMaxRange()));
                                
                                end = ProxyClient.findHit(world, entityPlayer, start, end);
                                
                                if(laser.isPoint() && !ProxyClient.tmpHitNothing)
                                {
                                    ProxyClient.renderLaserPoint(b.getBuffer(ProxyClient.LASER_POINT), matrixStack, laser, start, end);
                                    b.finish(ProxyClient.LASER_POINT);
                                }
                                
                                if(laser.isBeam())
                                {
                                    ProxyClient.renderLaserBeam(b.getBuffer(ProxyClient.LASER_BEAM), matrixStack, laser, start, end);
                                    b.finish(ProxyClient.LASER_BEAM);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        matrixStack.pop();
    }
    
    public static void renderLaserPoint(IVertexBuilder b, MatrixStack m, Laser laser, Vec3d start, Vec3d end)
    {
        Matrix4f matrix = m.getLast().getMatrix();
        
        final float size = 0.05F;
        
        b.pos(matrix, (float)end.x + size, (float)(float)end.y + size, (float)(float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y + size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y + size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z + size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        
        b.pos(matrix, (float)end.x - size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x - size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y + size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x + size, (float)end.y - size, (float)end.z - size).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
    }
    
    public static void renderLaserBeam(IVertexBuilder b, MatrixStack m, Laser laser, Vec3d start, Vec3d end)
    {
        Matrix4f matrix = m.getLast().getMatrix();
        
        b.pos(matrix, (float)start.x, (float)start.y, (float)start.z).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
        b.pos(matrix, (float)end.x, (float)end.y, (float)end.z).color(laser.getR(), laser.getG(), laser.getB(), 1F).endVertex();
    }
    
    public static Vec3d getVectorForRotation(float pitch, float yaw)
    {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d(f1 * f2, f3, f * f2);
    }
    
    public static Vec3d getOffsetForHandRaw(PlayerEntity entityPlayer, Hand hand)
    {
        Vec3d vec = ProxyClient.getVectorForRotation(entityPlayer.rotationPitch + 1, entityPlayer.rotationYaw + 90F);
        
        if(hand == Hand.OFF_HAND)
        {
            vec = vec.scale(-1);
        }
        
        if(entityPlayer.rotationPitch >= 89)
        {
            vec = vec.scale(-1);
        }
        
        return new Vec3d(vec.x, 0, vec.z).normalize().scale(0.4D);
    }
    
    public static Vec3d getOffsetForHand(PlayerEntity entityPlayer, Hand hand)
    {
        Vec3d vec = ProxyClient.getOffsetForHandRaw(entityPlayer, hand);
        
        return vec.add(ProxyClient.getVectorForRotation(entityPlayer.rotationPitch, entityPlayer.rotationYaw).scale(0.4D));
    }
    
    private static boolean tmpHitNothing = false;
    
    public static Vec3d findHit(World world, Entity entity, Vec3d start, Vec3d end)
    {
        ProxyClient.tmpHitNothing = false;
        
        BlockRayTraceResult resultBlock = ProxyClient.findBlockOnPath(world, entity, start, end);
        EntityRayTraceResult resultEntity = ProxyClient.findEntityOnPath(world, entity, start, end);
        
        if(resultEntity != null)
        {
            double rangeBlockSq = resultBlock.getHitVec().squareDistanceTo(start);
            double rangeEntitySq = resultEntity.getHitVec().squareDistanceTo(start);
            
            if(rangeBlockSq < rangeEntitySq)
            {
                return resultBlock.getHitVec();
            }
            else
            {
                return resultEntity.getHitVec();
            }
            
        }
        else
        {
            ProxyClient.tmpHitNothing = world.getBlockState(resultBlock.getPos()).getBlock() == Blocks.AIR;
            return resultBlock.getHitVec();
        }
    }
    
    public static BlockRayTraceResult findBlockOnPath(World world, Entity entity, Vec3d start, Vec3d end)
    {
        return world.rayTraceBlocks(new RayTraceContext(start, end, BlockMode.COLLIDER, FluidMode.NONE, entity));
    }
    
    public static EntityRayTraceResult findEntityOnPath(World world, Entity entity0, Vec3d start, Vec3d end)
    {
        EntityRayTraceResult result = null;
        Optional<Vec3d> opt;
        
        double rangeSq = 0;
        double currentRangeSq;
        
        for(Entity entity : world.getEntitiesInAABBexcluding(entity0, GunCusUtility.aabbFromVec3ds(start, end), (entity1) -> true))
        {
            if(entity != null && (entity.getEntityId() != entity0.getEntityId()) && !(entity instanceof EntityBullet))
            {
                AxisAlignedBB axisalignedbb = entity.getBoundingBox().grow(0.30000001192092896D);
                
                opt = axisalignedbb.rayTrace(start, end);
                
                if(opt.isPresent())
                {
                    currentRangeSq = start.squareDistanceTo(entity.getPositionVec());
                    
                    if(currentRangeSq < rangeSq || result == null)
                    {
                        result = new EntityRayTraceResult(entity, opt.get());
                        rangeSq = currentRangeSq;
                    }
                }
            }
        }
        return result;
    }
    
    public static Minecraft getMC()
    {
        return Minecraft.getInstance();
    }
    
    @Nullable
    public static PlayerEntity getClientPlayer()
    {
        return ProxyClient.getMC().player;
    }
}
