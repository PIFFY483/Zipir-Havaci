package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zipirhavaci.entity.BlastTntEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class BlastTntRenderer extends EntityRenderer<BlastTntEntity> {

    private static final java.util.Map<String, BlockState> STATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, float[]> DOMINANT_COLOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();


    private final BlockRenderDispatcher blockRenderer;

    public BlastTntRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
        this.shadowRadius = 0.5f;
    }


    private float[] getDominantColor(String originType, BlockState blockState) {
        return DOMINANT_COLOR_CACHE.computeIfAbsent(originType, key -> {
            try {
                net.minecraft.client.resources.model.BakedModel model =
                        Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);

                // Texture'ü bul (Senin yazdığın quad tarama mantığı)
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = null;
                for (Direction d : Direction.values()) {
                    var quads = model.getQuads(blockState, d, net.minecraft.util.RandomSource.create(0));
                    if (!quads.isEmpty()) { sprite = quads.get(0).getSprite(); break; }
                }
                if (sprite == null) {
                    var quads = model.getQuads(blockState, null, net.minecraft.util.RandomSource.create(0), net.minecraftforge.client.model.data.ModelData.EMPTY, null);
                    if (!quads.isEmpty()) sprite = quads.get(0).getSprite();
                }

                if (sprite != null) {
                    int width = sprite.contents().width();
                    int height = sprite.contents().height();
                    long rSum = 0, gSum = 0, bSum = 0, count = 0;

                    for (int py = 0; py < height; py++) {
                        for (int px = 0; px < width; px++) {
                            int argb = sprite.contents().getOriginalImage().getPixelRGBA(px, py);
                            int a = (argb >> 24) & 0xFF;
                            if (a < 128) continue;
                            rSum += (argb) & 0xFF;
                            gSum += (argb >> 8) & 0xFF;
                            bSum += (argb >> 16) & 0xFF;
                            count++;
                        }
                    }
                    if (count > 0) {
                        return new float[]{ (rSum/count)/255f, (gSum/count)/255f, (bSum/count)/255f };
                    }
                }
            } catch (Exception ignored) {}

            // Varsayılan Vanilla TNT
            return new float[]{1.0f, 0.15f, 0.0f};
        });
    }


    private float[] getDynamicFlashColor(BlastTntEntity entity, float[] dominantColor) {
        long seed = (long)entity.getId() + (entity.getFuse() / 4);
        java.util.Random rng = new java.util.Random(seed);

        // 1. DURUM: VANILLA MINECRAFT TNT (Turuncu - Kırmızı Korunuyor)
        if (entity.getOriginType().equals("minecraft:tnt")) {
            return new float[]{1.0f, rng.nextFloat() * 0.5f, 0.0f};
        }

        // 2. DURUM: MODDED TNT


        float randR = rng.nextFloat();
        float randG = 0.3f + (rng.nextFloat() * 0.4f);
        float randB = 0.5f + (rng.nextFloat() * 0.5f);

        // Karışım Hesaplama
        float finalR = (dominantColor[0] * 0.55f) + (randR * 0.45f);
        float finalG = (dominantColor[1] * 0.55f) + (randG * 0.45f);
        float finalB = (dominantColor[2] * 0.55f) + (randB * 0.45f);

        // PARLAKLIK KONTROLÜ (Luminance check)
        float brightness = (finalR * 0.2126f + finalG * 0.7152f + finalB * 0.0722f);

        if (brightness < 0.45f) {
            float boost = 0.45f / Math.max(brightness, 0.01f);
            finalR = Math.min(1.0f, finalR * boost);
            finalG = Math.min(1.0f, finalG * boost);
            finalB = Math.min(1.0f, finalB * boost);
        }

        // Ekstra Güvenlik: minimum beyazlık ekle
        return new float[]{
                Math.max(finalR, 0.2f),
                Math.max(finalG, 0.2f),
                Math.max(finalB, 0.2f)
        };
    }



    @Override
    public void render(BlastTntEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // ── Pulse hesabı ─────────────────────────────────────────────────
        int fuse = entity.getFuse();
        boolean flashing = (fuse / 4) % 2 == 0;

        double pulseRad = (fuse - partialTick) * (Math.PI / 8.0);
        float pulse = (float)(Math.sin(pulseRad) * 0.5 + 0.5);

        // ── Smooth rotation interpolasyon ────────────────────────────────
        float interpYaw   = entity.prevVisualYaw   + (entity.visualYaw   - entity.prevVisualYaw)   * partialTick;
        float interpPitch = entity.prevVisualPitch + (entity.visualPitch - entity.prevVisualPitch) * partialTick;

        poseStack.translate(0.5, 0.5, 0.5);

        // Smooth büyüme flash anında
        if (flashing) {
            float scale = 1.0f + 0.05f * pulse;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(interpYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(interpPitch));

        poseStack.translate(-0.5, -0.5, -0.5);

        // ── Block state ──────────────────────────────────────────────────
        BlockState blockState = resolveBlockState(entity.getOriginType());

        // ── Pass 1: normal render ─────────────────────────────────────────
        blockRenderer.renderSingleBlock(blockState, poseStack, buffer,
                flashing ? LightTexture.FULL_BRIGHT : packedLight,
                OverlayTexture.NO_OVERLAY);

        // ── Pass 2: flash anında renk overlay ────────────────────────────
        if (flashing) {
            float[] domColor = getDominantColor(entity.getOriginType(), blockState);
            float[] flashColor = getDynamicFlashColor(entity, domColor);

            // Maske şiddeti
            float alpha = 0.5f + (pulse * 0.4f);

            poseStack.pushPose();
            // Z-fighting i önle
            poseStack.scale(1.001f, 1.001f, 1.001f);
            poseStack.translate(-0.0005f, -0.0005f, -0.0005f);

            renderDynamicHurtMask(poseStack, buffer, blockState, entity,
                    flashColor[0], flashColor[1], flashColor[2], alpha);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }




    private void renderDynamicHurtMask(PoseStack poseStack, MultiBufferSource buffer,
                                       BlockState state, BlastTntEntity entity,
                                       float r, float g, float b, float alpha) {

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        net.minecraft.client.resources.model.BakedModel model = dispatcher.getBlockModel(state);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));

        PoseStack.Pose lastPose = poseStack.last();

        // Bloğun tüm yüzlerini çiz
        for (Direction direction : Direction.values()) {
            renderQuads(lastPose, consumer, model.getQuads(state, direction, net.minecraft.util.RandomSource.create(42L)), r, g, b, alpha);
        }
        renderQuads(lastPose, consumer, model.getQuads(state, null, net.minecraft.util.RandomSource.create(42L)), r, g, b, alpha);
    }

    private void renderQuads(PoseStack.Pose pose, VertexConsumer consumer,
                             java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                             float r, float g, float b, float a) {
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, r, g, b, a, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
    }



    private BlockState resolveBlockState(String originTypeStr) {
        // 1. Eğer bu TNT tipi daha önce çözüldüyse, doğrudan hafızadan (RAM) getir.
        // 2. Eğer ilk kez görülüyorsa, içindeki fonksiyonu sadece BİR KEZ çalıştırır.
        return STATE_CACHE.computeIfAbsent(originTypeStr, (key) -> {
            try {
                // "minecraft:tnt" ise veya boşsa direkt vanilla dön
                if (key == null || key.isEmpty() || key.equals("minecraft:tnt")) {
                    return Blocks.TNT.defaultBlockState();
                }

                ResourceLocation typeId = new ResourceLocation(key);
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);

                // Dinamik Kontrol
                if (type != null && type != EntityType.TNT) {
                    // Entity ID si ile aynı olan Bloğu ara
                    ResourceLocation blockId = new ResourceLocation(typeId.getNamespace(), typeId.getPath());
                    var block = ForgeRegistries.BLOCKS.getValue(blockId);
                    if (block != null && block != Blocks.AIR) return block.defaultBlockState();

                    // Alternatif
                    ResourceLocation blockId2 = new ResourceLocation(typeId.getNamespace(), "tnt");
                    var block2 = ForgeRegistries.BLOCKS.getValue(blockId2);
                    if (block2 != null && block2 != Blocks.AIR) return block2.defaultBlockState();
                }
            } catch (Exception ignored) {
                // Hatalı ID gelirse patlama, vanilla TNT'ye dön
            }
            return Blocks.TNT.defaultBlockState();
        });
    }

    @Override
    public ResourceLocation getTextureLocation(BlastTntEntity entity) {
        return new ResourceLocation("minecraft", "textures/block/tnt_side.png");
    }

    @Override
    public boolean shouldRender(BlastTntEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true;
    }
}