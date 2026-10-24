package com.spotifyclient.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com/terraformersmc/modmenu/util/mod/fabric/FabricMod", remap = false)
abstract class ModMenuCreditsMixin {
    @Shadow(remap = false)
    @Final
    protected ModMetadata metadata;

    @Inject(method = "getCredits", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void versefall$credits(CallbackInfoReturnable<Object> cir) {
        Set<String> authors = this.versefall$authors();
        if (!authors.isEmpty()) {
            if (cir.getReturnValue() instanceof List) {
                cir.setReturnValue(new ArrayList<>(authors));
            } else {
                SortedMap<String, Set<String>> credits = new TreeMap<>();
                credits.put("Author", authors);
                cir.setReturnValue(credits);
            }
        }
    }

    @Unique
    private Set<String> versefall$authors() {
        Set<String> authors = new LinkedHashSet<>();
        if (this.metadata != null && "spotify_mod".equals(this.metadata.getId())) {
            for (Person p : this.metadata.getContributors()) {
                authors.add(p.getName());
            }

            return authors;
        } else {
            return authors;
        }
    }
}
