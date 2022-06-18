package qupath.ext.ductales.commands;

import java.util.Collection;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.ext.ductales.CellsInfoExtractor;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.ext.ductales.utils.ParameterPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.PathCellObject;

public class MeasureCellsInfosCommand implements Runnable {
	private QuPathGUI qupath;
	private Stage configDialog;
	private ParameterPane parameterPane;

	public MeasureCellsInfosCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if(configDialog == null) {
			configDialog = new Stage();
			configDialog.setResizable(false);
			configDialog.setTitle("Measuring cells infos...");
			configDialog.setOnCloseRequest(e -> configDialog = null);
			configDialog.initOwner(qupath.getStage());

			parameterPane = new ParameterPane();

			parameterPane.addCheckbox("measureShape", "Measure shape", DuctalesConstants.DEFAULT_CELL_MEASURE_SHAPE);
			parameterPane.addCheckbox("measureIntensity", "Measure intensity", DuctalesConstants.DEFAULT_CELL_MEASURE_INTENSITY);
			parameterPane.addCheckbox("measureTexture", "Measure texture", DuctalesConstants.DEFAULT_CELL_MEASURE_TEXTURE);

			parameterPane.addButton("Measure", e -> onMeasureButtonClicked(e), true);

			configDialog.setScene(new Scene(parameterPane));
		}else 
			configDialog.requestFocus();

		configDialog.sizeToScene();
		configDialog.show();
	}

	private void onMeasureButtonClicked(MouseEvent event){
		parameterPane.saveParametersInCache();

		var curImage = qupath.getImageData();
		var cells = (Collection<PathCellObject>)(Object)curImage.getHierarchy().getCellObjects();

		new CellsInfoExtractor()
		.measureShape((boolean)parameterPane.getParameters().get("measureShape"))
		.measureIntensity((boolean)parameterPane.getParameters().get("measureIntensity"))
		.measureTexture((boolean)parameterPane.getParameters().get("measureTexture"))
		.extract(curImage, cells);
	}
}
