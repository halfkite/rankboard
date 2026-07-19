package cn.bamgdam.rankboard.mixin;

import cn.bamgdam.rankboard.PlayerNameColors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void rankboard$colorDisplayName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof ServerPlayer player) {
            callback.setReturnValue(PlayerNameColors.decorate(player, callback.getReturnValue()));
        }
    }
}
