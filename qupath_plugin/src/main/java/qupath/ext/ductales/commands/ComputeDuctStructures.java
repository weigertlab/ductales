package qupath.ext.ductales.commands;

import java.util.Arrays;
import java.util.Collection;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.ext.ductales.DuctStructureComputer;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.ext.ductales.utils.ParameterPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.objects.PathCellObject;

public class ComputeDuctStructures implements Runnable {
	private QuPathGUI qupath;
	private Stage configDialog;
	private ParameterPane parameterPane;

	public ComputeDuctStructures(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if(configDialog == null) {
			configDialog = new Stage();
			configDialog.setResizable(false);
			configDialog.setTitle("Compute duct structures...");
			configDialog.setOnCloseRequest(e -> configDialog = null);
			configDialog.initOwner(qupath.getStage());

			parameterPane = new ParameterPane();
			parameterPane.addSeparator("Aggregation parameters");
			parameterPane.addStringListCreator("excludedClasses", "Excluded classes (no duct)", DuctalesConstants.DEFAULT_NO_DUCT_CLASSES);
			parameterPane.addDoubleTextField("ductMaxDistance", "Duct max distance (um)", DuctalesConstants.DEFAULT_DUCT_STRUCTURE_MAX_DISTANCE);
			parameterPane.addIntegerTextField("ductMinCellSize", "Duct min cell size", DuctalesConstants.DEFAULT_DUCT_STRUCTURE_MIN_CELL_SIZE);

			parameterPane.addSeparator("Measure parameters");
			parameterPane.addStringListCreator("ductClasses", "Duct classes", DuctalesConstants.DEFAULT_DUCT_CLASSES);
			parameterPane.addDoubleListCreator("holesMinDistances", "Holes min distances (um)", DuctalesConstants.DEFAULT_DUCT_HOLES_MIN_DISTANCES);
			parameterPane.addIntegerTextField("holesMinCellSize", "Hole min cell size", DuctalesConstants.DEFAULT_DUCT_HOLES_MIN_CELL_SIZE);
			parameterPane.addCheckbox("refineBoundaries", "Refine boundaries", DuctalesConstants.DEFAULT_REFINE_BOUNDARIES);
			parameterPane.addDoubleTextField("triangleToRefineMinAngle", "Triangle to refine min angle (deg)", DuctalesConstants.DEFAULT_TRIANGLE_TO_REFINE_MIN_ANGLE);

			parameterPane.addSeparator("Other");
			parameterPane.addCheckbox("showHoles", "Show holes", DuctalesConstants.DEFAULT_SHOW_HOLES);
			parameterPane.addCheckbox("showPerimeters", "Show perimeters", DuctalesConstants.DEFAULT_SHOW_PERIMETERS);
			parameterPane.addCheckbox("showDelaunay", "Show delaunay graph", DuctalesConstants.DEFAULT_SHOW_DELAUNAY);

			parameterPane.addButton("Compute", e -> onComputeButtonClicked(e), true);

			configDialog.setScene(new Scene(parameterPane));
		}else 
			configDialog.requestFocus();

		configDialog.sizeToScene();
		configDialog.show();
	}

	private void onComputeButtonClicked(MouseEvent event){
		parameterPane.saveParametersInCache();

		var curImage = qupath.getImageData();
		var cells = (Collection<PathCellObject>)(Object)curImage.getHierarchy().getCellObjects();

		var ductMaxDistance = (double)parameterPane.getParameters().get("ductMaxDistance");
		var holesMinDistances = (double[])parameterPane.getParameters().get("holesMinDistances");
		var maxHolesDistances = Arrays.stream(holesMinDistances).max();

		if(ductMaxDistance < maxHolesDistances.getAsDouble()) {
			Dialogs.showErrorMessage("Error", "Duct max distance cannot be lower than holes min distances.");
			return;
		}

		var ductComputer = new DuctStructureComputer()
				.excludeClasses((String[])parameterPane.getParameters().get("excludedClasses"))
				.ductMaxDistance(ductMaxDistance)
				.ductMinCellSize((int)parameterPane.getParameters().get("ductMinCellSize"))
				.measure(true)
				.ductClasses((String[])parameterPane.getParameters().get("ductClasses"))
				.holesMinDistances(holesMinDistances)
				.holesMinCellSize((int)parameterPane.getParameters().get("holesMinCellSize"))
				.refineBoundaries((boolean)parameterPane.getParameters().get("refineBoundaries"))
				.triangleToRefineMinAngle((double)parameterPane.getParameters().get("triangleToRefineMinAngle"));
		var ducts = ductComputer.compute(curImage, cells);

		curImage.getHierarchy().addPathObjects(ducts);

		if((boolean)parameterPane.getParameters().get("showHoles")) {
			var holes = ductComputer.getHolesPathObjects();
			curImage.getHierarchy().addPathObjects(holes);
		}
		if((boolean)parameterPane.getParameters().get("showPerimeters")) {
			var perimeters = ductComputer.getPerimetersPathObjects();
			curImage.getHierarchy().addPathObjects(perimeters);
		}
		if((boolean)parameterPane.getParameters().get("showDelaunay")) {
			ductComputer.showDelaunay(curImage);
		}
	}
}
