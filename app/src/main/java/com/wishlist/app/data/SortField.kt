package com.wishlist.app.data

/** Sort criteria a category group can be sorted by, independently of every other group. */
enum class SortField {
    COMPLETED_AT,
    STARTED_AT,
    DURATION,
    TITLE,

    /**
     * The order the user dragged items into, stored per item as [com.wishlist.app.data.WishlistItem.position].
     * A group switches to this automatically when an item in it is dragged — any other sort would
     * immediately undo the drag.
     */
    MANUAL,
}
