package com.example.mobrandomizer.mixin;

import com.example.mobrandomizer.RandomDrops;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
    private void mobrandomizer$dropLoot(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world) || self instanceof PlayerEntity) return;
        RandomDrops.dropFor(self, world, source);
        ci.cancel();
    }
}
