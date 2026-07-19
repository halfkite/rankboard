package cn.bamgdam.rankboard.mixin;

import cn.bamgdam.rankboard.PlayerNameColors;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerListNameMixin {
    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void rankboard$colorPlayerListName(CallbackInfoReturnable<Text> callback) {
        callback.setReturnValue(PlayerNameColors.decorate((ServerPlayerEntity) (Object) this, callback.getReturnValue()));
    }
}
