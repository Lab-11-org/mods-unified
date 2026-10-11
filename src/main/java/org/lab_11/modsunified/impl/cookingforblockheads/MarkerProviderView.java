package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

public interface MarkerProviderView {
    boolean isMarkerKey(String markerKey);

    boolean isActiveForCurrentTableMarker();

    boolean isTargetPotConnectedForCurrentTableMarker();

    ItemStack netContainerRequired(Recipe<?> recipe, ItemStack fullContainerCost);
}
