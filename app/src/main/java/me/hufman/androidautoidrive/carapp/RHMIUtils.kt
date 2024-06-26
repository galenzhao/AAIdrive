package me.hufman.androidautoidrive.carapp

import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIProperty
import kotlin.math.abs

object RHMIUtils {
	/**
	 * Iterate through the given components to find the component that is
	 * horizontally aligned, to the right, to the component matched by the predicate
	 */
	fun findAdjacentComponent(components: Iterable<RHMIComponent>, nearTo: RHMIComponent?): RHMIComponent? {
		nearTo ?: return null
		val layout = getComponentLayout(nearTo)
		val nearToLocation = getComponentLocation(nearTo, layout)
		val neighbors = components.filter {
			val location = getComponentLocation(it, layout)
			abs(nearToLocation.second - location.second) < 10 && // same height
			nearToLocation.first < location.first    // first component left of second
		}
		return neighbors.minByOrNull {
			getComponentLocation(it, layout).first
		}
	}

	fun getComponentLayout(component: RHMIComponent): Int {
		val xProperty = component.properties[RHMIProperty.PropertyId.POSITION_X.id]
		return if (xProperty is RHMIProperty.LayoutBag) {
			xProperty.values.keys.firstOrNull { (xProperty.values[it] as Int) < 1600 } ?: 0
		} else {
			0
		}
	}
	fun getComponentLocation(component: RHMIComponent, layout: Int = 0): Pair<Int, Int> {
		val xProperty = component.properties[RHMIProperty.PropertyId.POSITION_X.id]
		val yProperty = component.properties[RHMIProperty.PropertyId.POSITION_Y.id]
		val x = xProperty?.getForLayout(layout) as? Int ?: -1
		val y = yProperty?.getForLayout(layout) as? Int ?: -1
		return Pair(x,y)
	}

}