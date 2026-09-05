package com.personalmoney.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * AdSense, which cannot live where the rest of the app lives.
 *
 * Compose draws the whole application into one `<canvas>`, and an ad unit is a DOM element
 * the ad network inserts an iframe into - there is no way to put one inside a canvas. So
 * the slot is split in two: this composable reserves the space in the Compose layout, and a
 * real `<div>` is parked over the canvas at exactly those coordinates. When the composable
 * scrolls, the div follows; when it leaves the composition, the div goes with it.
 *
 * The alternative - a fixed strip below the canvas, baked into the page - was rejected
 * because it puts the ad in the same place on every screen whether or not that screen asked
 * for one, and the shared screens already say where the slot belongs.
 *
 * Draws nothing until an AdSense account is configured (see `index.html`), which is also
 * what makes this safe to merge before there is one.
 *
 * **Not yet working, and not yet understood.** With the meta tags filled in, this body never
 * runs: a probe that stamps the document from inside [adSenseConfigured] shows it is never
 * called, on the overview and on the ledger alike, at a width where both demonstrably render
 * the branch that calls `BannerAd()`. It is not dead-code elimination - all four JS imports
 * are present in the wasm binary - and it is not the `enabled` flag, which was folded into
 * the same probe and never observed. Moving the `expect` declaration's default arguments
 * onto a plain composable in front of it changed nothing either. Whatever is in the way is
 * still in the way; the code below is the intended shape, not a working feature.
 */
@Composable
internal actual fun PlatformBannerAd(modifier: Modifier, enabled: Boolean) {
    if (!enabled || !adSenseConfigured()) return

    val density = LocalDensity.current.density
    val slotId = remember { nextAdSlotId() }

    DisposableEffect(slotId) {
        createAdSlot(slotId)
        onDispose { removeAdSlot(slotId) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT)
            .onGloballyPositioned { coordinates ->
                // Compose measures in its own pixels; the page positions in CSS pixels, and
                // `density` is exactly the ratio between them. Dividing here rather than
                // assuming they are the same keeps the slot in place on a display where
                // they are not.
                val bounds = coordinates.boundsInWindow()
                moveAdSlot(
                    id = slotId,
                    left = bounds.left / density,
                    top = bounds.top / density,
                    width = bounds.width / density,
                    height = bounds.height / density,
                )
            }
    )
}

/** The height AdSense's responsive unit settles on for a banner in a narrow column. */
private val BANNER_HEIGHT = 50.dp

private var slotCounter = 0

private fun nextAdSlotId(): String = "pm-ad-${slotCounter++}"

/**
 * Whether the page carries an AdSense publisher id.
 *
 * Read from the document rather than compiled in: a publisher id is public - it appears in
 * the source of every page that shows AdSense - so there is nothing to hide, and keeping it
 * in the shell means changing it does not mean rebuilding seven megabytes of wasm.
 */
@JsFun(
    """() => {
        const client = document.querySelector('meta[name="adsense-client"]')?.content;
        const slot = document.querySelector('meta[name="adsense-slot"]')?.content;
        return !!(client && slot && client.startsWith('ca-pub-'));
    }"""
)
private external fun adSenseConfigured(): Boolean

/**
 * Builds the unit and asks AdSense to fill it, once.
 *
 * `push({})` is what starts the fill, and it must happen exactly once per `<ins>` - calling
 * it again on an element that already holds an ad is what produces AdSense's "All
 * 'ins' elements in the DOM with class=adsbygoogle already have ads in them" error. Moving
 * the element afterwards is fine; re-pushing is not.
 */
@JsFun(
    """(id) => {
        const client = document.querySelector('meta[name="adsense-client"]').content;
        const slot = document.querySelector('meta[name="adsense-slot"]').content;

        const host = document.createElement('div');
        host.id = id;
        // Fixed, not absolute: the page itself never scrolls (Compose scrolls inside its
        // own containers), so the coordinates the layout reports are viewport coordinates.
        host.style.cssText = 'position:fixed;overflow:hidden;z-index:10;display:none';

        const ins = document.createElement('ins');
        ins.className = 'adsbygoogle';
        ins.style.cssText = 'display:block;width:100%;height:100%';
        ins.setAttribute('data-ad-client', client);
        ins.setAttribute('data-ad-slot', slot);
        ins.setAttribute('data-ad-format', 'horizontal');
        ins.setAttribute('data-full-width-responsive', 'false');
        host.appendChild(ins);

        document.body.appendChild(host);
        try {
            (window.adsbygoogle = window.adsbygoogle || []).push({});
        } catch (e) {
            // A blocked or missing script must not take the app down with it.
            console.warn('AdSense fill failed', e);
        }
    }"""
)
private external fun createAdSlot(id: String)

/**
 * Follows the Compose slot.
 *
 * Also hides the unit once the slot has scrolled out of the viewport: the element is
 * position-fixed, so without this a banner that scrolled off the top of a list would stay
 * pinned over whatever is now there.
 */
@JsFun(
    """(id, left, top, width, height) => {
        const host = document.getElementById(id);
        if (!host) return;
        const offscreen = top + height <= 0 || top >= window.innerHeight || width <= 0;
        host.style.display = offscreen ? 'none' : 'block';
        host.style.left = left + 'px';
        host.style.top = top + 'px';
        host.style.width = width + 'px';
        host.style.height = height + 'px';
    }"""
)
private external fun moveAdSlot(id: String, left: Float, top: Float, width: Float, height: Float)

@JsFun("(id) => document.getElementById(id)?.remove()")
private external fun removeAdSlot(id: String)
