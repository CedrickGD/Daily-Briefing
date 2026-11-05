package com.cedrickgd.morgenbriefing.ui.shell

/**
 * What opening a pushed screen (discover, search) does to the back stack, given the route on
 * top and the topmost route of the same kind further down (null when there is none). Pure, so
 * the rules are unit-tested:
 *
 * - the very same screen is already on top (a double tap): nothing happens;
 * - the same screen lies further down (search → discover → search): go back to it instead of
 *   stacking a second copy;
 * - otherwise push the new screen, replacing any other screen of the same kind (another
 *   category, another query), so the stack never holds two of a kind.
 */
internal enum class NavPlan {
    IGNORE,
    RETURN,
    PUSH,
    ;

    companion object {
        fun of(top: Any?, sameKindBelow: Any?, target: Any): NavPlan = when (target) {
            top -> IGNORE
            sameKindBelow -> RETURN
            else -> PUSH
        }
    }
}
