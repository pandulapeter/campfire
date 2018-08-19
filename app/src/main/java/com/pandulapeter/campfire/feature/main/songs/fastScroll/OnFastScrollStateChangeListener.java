package com.pandulapeter.campfire.feature.main.songs.fastScroll;

public interface OnFastScrollStateChangeListener {

    /**
     * Called when fast scrolling begins
     */
    void onFastScrollStart();

    /**
     * Called when fast scrolling ends
     */
    void onFastScrollStop();
}