package rendering

import korlibs.korge.view.View

/**
 * Result of a diff between the set of items currently represented by a view and
 * the desired (current) set of items.
 *
 * Keeping this as a data class makes the synchronization logic testable without
 * constructing KorGE views. The scene code can therefore rely on a small pure
 * function for the decision and keep side effects limited to [syncViews].
 *
 * @property toAdd Items to add (in [currentItems] but not in [currentlyTracked]).
 * @property toRemove Items to remove (in [currentlyTracked] but not in [currentItems]).
 */
data class ViewDiff<T>(val toAdd: Set<T>, val toRemove: Set<T>)

/**
 * Computes the set difference for reactive view synchronization.
 *
 * @param currentlyTracked Items that already have a view attached.
 * @param currentItems Items that should have a view attached now.
 * @return Items to add (in [currentItems] but not in [currentlyTracked]) and items to
 *         remove (in [currentlyTracked] but not in [currentItems]).
 */
fun <T> computeViewDiff(currentlyTracked: Set<T>, currentItems: Set<T>): ViewDiff<T> =
    ViewDiff(
        // Items present in the domain but missing from the view map need a new view.
        toAdd = currentItems - currentlyTracked,
        // Items still tracked by the UI but gone from the domain must be detached.
        toRemove = currentlyTracked - currentItems
    )

/**
 * Reactive view synchronisation: keeps [views] aligned with [items] by creating views via
 * [factory] for new items. [factory] is expected to attach the returned [View] to its parent.
 *
 * @param items The desired current set of items.
 * @param views Mutable map from item to its view; mutated in place.
 * @param factory Function that creates and attaches a new view for an item.
 */
fun <T : Any> syncViews(
    items: Set<T>,
    views: MutableMap<T, View>,
    factory: (T) -> View
) {
    // Compare the map keys with the domain set; keys are the items already represented on screen.
    val diff = computeViewDiff(views.keys.toSet(), items)
    for (removed in diff.toRemove) {
        // Detach the visual object first so it disappears from its parent container.
        views[removed]?.removeFromParent()
        // Then forget the item, otherwise later syncs would still treat it as rendered.
        views.remove(removed)
    }
    for (added in diff.toAdd) {
        // The factory owns the concrete view creation because callers know the correct layer and asset.
        views[added] = factory(added)
    }
}
