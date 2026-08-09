package org.lab_11.modsunified.impl.cookingforblockheads;

public interface MarkerProviderView {
    boolean isMarkerKey(String markerKey);

    boolean isActiveForCurrentTableMarker();

    boolean isTargetPotConnectedForCurrentTableMarker();
}
