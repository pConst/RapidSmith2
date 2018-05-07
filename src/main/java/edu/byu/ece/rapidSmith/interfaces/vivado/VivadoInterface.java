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

package edu.byu.ece.rapidSmith.interfaces.vivado;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

import edu.byu.ece.edif.core.EdifNameConflictException;
import edu.byu.ece.edif.core.InvalidEdifNameException;
import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.design.subsite.*;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.util.Exceptions;
import org.apache.commons.lang3.tuple.MutablePair;

/**
 * This class is used to interface Vivado and RapidSmith2. 
 * It parses RSCP checkpoints and creates equivalent RapidSmith {@link CellDesign}s.
 * It can also create TINCR checkpoints from existing RapidSmith {@link CellDesign}s.
 */
public final class VivadoInterface {

	private static final String CELL_LIBRARY_NAME = "cellLibrary.xml";
	
	public static VivadoCheckpoint loadRSCP(String rscp) throws IOException {
		return loadRSCP(rscp, false);
	}
	
	/**
	 * Parses a RSCP generated from Tincr, and creates an equivalent RapidSmith2 design.
	 * 
	 * @param rscp Path to the RSCP to import
	 * @throws InvalidEdifNameException 
	 * @throws EdifNameConflictException 
	 */
	public static VivadoCheckpoint loadRSCP (String rscp, boolean storeAdditionalInfo) throws IOException {
	
		Path rscpPath = Paths.get(rscp);
		
		if (!rscpPath.getFileName().toString().endsWith(".rscp")) {
			throw new AssertionError("Specified directory is not a RSCP. The directory should end in \".rscp\"");
		}
					
		// load the device
		DesignInfoInterface designInfo = new DesignInfoInterface();
		designInfo.parse(rscpPath);
		String partName = designInfo.getPart();
		ImplementationMode mode = designInfo.getMode();
		if (partName == null) {
			throw new Exceptions.ParseException("Part name for the design not found in the design.info file!");
		}
		
		Device device = RSEnvironment.defaultEnv().getDevice(partName);
		
		if (device == null) {
			throw new Exceptions.EnvironmentException("Device files for part: " + partName + " cannot be found.");
		}

		// load the cell library
		CellLibrary libCells = new CellLibrary(RSEnvironment.defaultEnv()
				.getPartFolderPath(partName)
				.resolve(CELL_LIBRARY_NAME));
		
		// add additional macro cell specifications to the cell library before parsing the EDIF netlist
		libCells.loadMacroXML(rscpPath.resolve("macros.xml"));
		
		// create the RS2 netlist
		String edifFile = rscpPath.resolve("netlist.edf").toString();

		CellDesign design;
		// TODO: Make a PARTIAL_RECONFIG mode.
		//if (mode.equals(ImplementationMode.OUT_OF_CONTEXT))
		//	design = EdifInterface.parseEdif(edifFile, libCells, partName);
		//else
			design = EdifInterface.parseEdif(edifFile, libCells);

		design.setImplementationMode(mode);
		
		// parse the constraints into RapidSmith
		String constraintsFile = rscpPath.resolve("constraints.xdc").toString();
		XdcConstraintsInterface constraintsInterface = new XdcConstraintsInterface(design, device);
		constraintsInterface.parseConstraintsXDC(constraintsFile);

		// re-create the placement and routing information
		String placementFile = rscpPath.resolve("placement.rsc").toString();
		XdcPlacementInterface placementInterface = new XdcPlacementInterface(design, device);
		placementInterface.parsePlacementXDC(placementFile);
 
		String routingFile = rscpPath.resolve("routing.rsc").toString();
		XdcRoutingInterface routingInterface = new XdcRoutingInterface(design, device, placementInterface.getPinMap(), mode);
		routingInterface.parseRoutingXDC(routingFile);
		design.setOocPortMap(routingInterface.getOocPortMap());

		VivadoCheckpoint vivadoCheckpoint = new VivadoCheckpoint(partName, design, device, libCells);

		if (storeAdditionalInfo) {
			vivadoCheckpoint.setRoutethroughBels(routingInterface.getRoutethroughsBels());
			vivadoCheckpoint.setStaticSourceBels(routingInterface.getStaticSourceBels());
			vivadoCheckpoint.setBelPinToCellPinMap(placementInterface.getPinMap());
		}

		// Mark the used static resources
		if (mode == ImplementationMode.RECONFIG_MODULE) {
			String resourcesFile = rscpPath.resolve("static_resources.rsc").toString();
			UsedStaticResources staticResources = new UsedStaticResources(design, device);
			staticResources.parseResourcesRSC(resourcesFile);
			vivadoCheckpoint.setStaticRoutemap(staticResources.getStaticRoutemap());
			design.setOocPortMap(staticResources.getOocPortMap());

			// It's possible the RM netlist may include OOC ports (partition pins) that are not used by the RM.
			// One reason this can happen is other RMs (for the same RP) may actually use these partition pins, while
			// this one does not. These ports need to remain in the netlist for the PR flow to work correctly.
			// For now, disconnect the pins from nets to make packing easier?
			// See partial count design for example of this.
//			for (Iterator<Cell> iterator = design.getPorts().iterator(); iterator.hasNext();) {
//				Cell port = iterator.next();
//
	//			for (CellPin pin : port.getPins()) {
	//				if (!pin.isPartitionPin()) {
	//					if (pin.getNet() != null) {
	//						pin.getNet().disconnectFromPin(pin);
	//					}
	//				}
	//
	//		   }
	//		}
		}


		return vivadoCheckpoint;
	}

	public static void writeTCP(String tcpDirectory, CellDesign design, Device device, CellLibrary libCells, ImplementationMode mode) throws IOException {
		writeTCP(tcpDirectory, design, device, libCells, mode, null);
	}


	/**
	 * Export the RapidSmith2 design into an existing TINCR checkpoint file. 
	 *   
	 * @param tcpDirectory TINCR checkpoint directory to write XDC files to
	 * @param design CellDesign to convert to a TINCR checkpoint
	 * @throws IOException
	 * @throws InvalidEdifNameException 
	 * @throws EdifNameConflictException 
	 */
	public static void writeTCP(String tcpDirectory, CellDesign design, Device device, CellLibrary libCells, ImplementationMode mode, Map<String, MutablePair<String, String>> staticRoutemap) throws IOException {
				
		new File(tcpDirectory).mkdir();
		
		// insert routethrough buffers
		LutRoutethroughInserter inserter = new LutRoutethroughInserter(design, libCells);
		inserter.execute();
		
		// Write placement.xdc
		String placementOut = Paths.get(tcpDirectory, "placement.xdc").toString();	
		XdcPlacementInterface placementInterface = new XdcPlacementInterface(design, device);
		placementInterface.writePlacementXDC(placementOut);
		
		// Write routing.xdc
		String routingOut = Paths.get(tcpDirectory, "routing.xdc").toString();
		String partpinRoutingOut = Paths.get(tcpDirectory, "partpin_routing.xdc").toString();

		XdcRoutingInterface routingInterface = new XdcRoutingInterface(design, device, null, mode, staticRoutemap);
		routingInterface.writeRoutingXDC(routingOut, partpinRoutingOut, design);
		
		// Write EDIF netlist
		String edifOut = Paths.get(tcpDirectory, "netlist.edf").toString();
		EdifInterface.writeEdif(edifOut, design);

		// write constraints.xdc
		String constraintsOut = Paths.get(tcpDirectory, "constraints.xdc").toString();
		XdcConstraintsInterface constraintsInterface = new XdcConstraintsInterface(design, device);
		constraintsInterface.writeConstraintsXdc(constraintsOut);

		// write design.info
		String partInfoOut = Paths.get(tcpDirectory, "design.info").toString();
		DesignInfoInterface.writeInfoFile(partInfoOut, design.getPartName());
	}
} // END CLASS 
