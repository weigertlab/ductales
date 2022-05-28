package qupath.ext.ductales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.ductales.commands.ComputeDuctStructures;
import qupath.ext.ductales.commands.DetectCellsCommand;
import qupath.ext.ductales.commands.FindDuctRegionsCommand;
import qupath.ext.ductales.commands.MeasureCellsInfosCommand;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

public class DuctalesExtension implements QuPathExtension{
	private final static Logger logger = LoggerFactory.getLogger(DuctalesExtension.class);
	
	private static boolean alreadyInstalled = false;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (alreadyInstalled)
			return;
		
		alreadyInstalled = true;

		logger.debug("Installing Ductales extension");
		
		var actionFindDuctRegions = ActionTools.createAction(new FindDuctRegionsCommand(qupath), "Find duct regions");
		var actionDetectNuclei = ActionTools.createAction(new DetectCellsCommand(qupath), "Detect nuclei");
		var actionMeasureCellsInfos = ActionTools.createAction(new MeasureCellsInfosCommand(qupath), "Measure cells infos");
		var actionComputeDuctStructure = ActionTools.createAction(new ComputeDuctStructures(qupath), "Compute duct structures");
		
		// Disable actions if no image opened
		actionFindDuctRegions.disabledProperty().bind(qupath.imageDataProperty().isNull());
		actionDetectNuclei.disabledProperty().bind(qupath.imageDataProperty().isNull());
		actionMeasureCellsInfos.disabledProperty().bind(qupath.imageDataProperty().isNull());
		actionComputeDuctStructure.disabledProperty().bind(qupath.imageDataProperty().isNull());

		MenuTools.addMenuItems(qupath.getMenu("Extensions", false), MenuTools.createMenu("Ductales", actionFindDuctRegions, actionDetectNuclei, actionMeasureCellsInfos, actionComputeDuctStructure));
	}

	@Override
	public String getName() {
		return "Ductales extension";
	}

	@Override
	public String getDescription() {
		return "Detect and characterize ducts from nuclei segmentation.";
	}
	
}
