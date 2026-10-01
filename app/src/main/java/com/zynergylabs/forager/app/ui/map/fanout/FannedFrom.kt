package com.zynergylabs.forager.app.ui.map.fanout

/**
 * A fan the user just picked an icon from (dispatch 2026-09-28-381): the [tapped] marker's key, and the keys of every member the fan held at that
 * moment, [tapped] included. The fan folds on that one tap, so by the time the tapped find's bubble offers "Open in Journal" the open fan is gone;
 * this is what Back from the find's page brings back.
 */
data class FannedFrom(val tapped: FanKey, val members: List<FanKey>)
