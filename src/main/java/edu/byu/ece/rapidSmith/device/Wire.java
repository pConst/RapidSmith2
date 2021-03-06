/*
 * Copyright (c) 2016 Brigham Young University
 *
 * This file is part of the BYU RapidSmith Tools.
 *
 * BYU RapidSmith Tools is free software: you may redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * BYU RapidSmith Tools is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU General Public License is included with the BYU
 * RapidSmith Tools. It can be found at doc/LICENSE.GPL3.TXT. You may
 * also get a copy of the license at <http://www.gnu.org/licenses/>.
 */

package edu.byu.ece.rapidSmith.device;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/**
 * Wires represent a piece of metal on a device.  Wires are composed of two
 * components, a location such as a tile or primitive site where the tile resides
 * and an enumeration identifying the unique wire inside the resource.  Wire
 * object are immutable.
 */
public interface Wire extends Serializable {
	int getWireEnum();
	String getName();
	String getFullName();
	Tile getTile();
	Site getSite();
	
	/**
	 * @deprecated Use {@link #getName} instead.
	 */
	@Deprecated
	String getWireName();
	
	/**
	 * @deprecated Use {@link #getFullName} instead.
	 */
	@Deprecated
	String getFullWireName();

	/**
	 * Return connection linking this wire to other wires in the same hierarchy.
	 */
	Collection<Connection> getWireConnections();
	
	WireConnection[] getWireConnectionsArray();

	/**
	 * Returns the connection to the given sink wire, if it exists
	 * @param sinkWire the sink wire to get a connection to
	 * @return the wire connection to sinkWire
	 */
	Connection getWireConnection(Wire sinkWire);

	/**
	 * Returns the connected site pins for each possible type of the connected site.
	 * @return all connected sites pins of this wire
	 */
	Collection<SitePin> getAllConnectedPins();

	/**
	 * Returns connection linking this wire to another wire in a different
	 * hierarchical level through a pin.
	 */
	SitePin getConnectedPin();

	/**
	 * Returns connection linking this wire to another wire in a different
	 * hierarchical level through a pin.
	 */
	BelPin getTerminal();

	/**
	 * Returns connection linking this wire to its drivers in the same hierarchy.
	 */
	Collection<Connection> getReverseWireConnections();
	
	WireConnection[] getReverseWireConnectionsArray();

	/**
	 * Returns the connected site pins for each possible type of the connected site.
	 * @return all connected sites pins of this wire
	 */
	Collection<SitePin> getAllReverseSitePins();

	/**
	 * Return connection linking this wire to its drivers in the different
	 * levels of hierarchy.
	 */
	SitePin getReverseConnectedPin();

	/**
	 * Returns the sources (BelPins) which drive this wire.
	 */
	BelPin getSource();

	/**
	 * Returns all beginning and end wires that make up a node. Does not include intermediate wires.
	 * @return beginning and end wires of a node.
	 */
	Set<Wire> getWiresInNode();
}
