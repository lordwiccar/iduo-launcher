package media.whitewhale.iduo

/** A cell index numbered as in a four-column grid of eight rows, in the current five-column storage. */
fun cell4(index: Int) = upgradeLegacyCellIndex(index, GRID_ROWS)

/** Home slots written as a four-column grid of eight rows, in the current five-column storage. */
fun fourColumns(vararg ids: String?): List<String?> = upgradeLegacySlots(ids.toList(), GRID_ROWS)
