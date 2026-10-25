package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.render.CustomFonts;
import java.util.Arrays;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @SuppressWarnings("ModifyVariableMayUseName")
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static RepositorySource[] skysong$addCustomFonts(RepositorySource[] repositorySources) {
        for (RepositorySource source : repositorySources) {
            if (source instanceof ClientPackSource) {
                RepositorySource[] extended = Arrays.copyOf(repositorySources, repositorySources.length + 1);
                extended[repositorySources.length] = CustomFonts.source();
                return extended;
            }
        }

        return repositorySources;
    }
}
