package cn.bamgdam.rankboard.mixin;

import cn.bamgdam.rankboard.PlayerNameColors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerListNameMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void rankboard$colorPlayerListName(CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(PlayerNameColors.decorate((ServerPlayer) (Object) this, callback.getReturnValue()));
    }
}
