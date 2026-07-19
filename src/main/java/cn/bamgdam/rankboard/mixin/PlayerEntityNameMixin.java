package cn.bamgdam.rankboard.mixin;

import cn.bamgdam.rankboard.PlayerNameColors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void rankboard$colorDisplayName(CallbackInfoReturnable<Text> callback) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            callback.setReturnValue(PlayerNameColors.decorate(player, callback.getReturnValue()));
        }
    }
}
