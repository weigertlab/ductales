package qupath.ext.ductales.commands;

import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.ext.ductales.CellsDetector;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.ext.ductales.utils.ParameterPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.roi.interfaces.ROI;

public class DetectCellsCommand implements Runnable {
	private QuPathGUI qupath;
	private Stage configDialog;
	private ParameterPane parameterPane;

	public DetectCellsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if(configDialog == null) {
			configDialog = new Stage();
			configDialog.setResizable(false);
			configDialog.setTitle("Detect cells...");
			configDialog.setOnCloseRequest(e -> configDialog = null);
			configDialog.initOwner(qupath.getStage());

			parameterPane = new ParameterPane();

			parameterPane.addSeparator("Image parameters");
			parameterPane.addMultiSelectionComboBox("channels", "Channels", DuctalesConstants.DEFAULT_STARDIST_CHANNELS, getChannelNames());

			parameterPane.addSeparator("StarDist parameters");
			parameterPane.addFileSelector("modelPath", "Model file", "", "Model file", ".pb");
			parameterPane.addDoubleTextField("threshold", "Threshold", DuctalesConstants.DEFAULT_STARDIST_THRESHOLD);
			parameterPane.addDoubleTextField("normalizePercMin", "Normalize percentile min", DuctalesConstants.DEFAULT_STARDIST_NORMALIZE_PERCENTILE_MIN);
			parameterPane.addDoubleTextField("normalizePercMax", "Normalize percentile max", DuctalesConstants.DEFAULT_STARDIST_NORMALIZE_PERCENTILE_MAX);
			parameterPane.addIntegerTextField("tileSize", "Tile size", DuctalesConstants.DEFAULT_STARDIST_TILE_SIZE);
			parameterPane.addIntegerTextField("tileOverlap", "Tile overlap", DuctalesConstants.DEFAULT_STARDIST_TILE_OVERLAP);
			parameterPane.addStringListCreator("classes", "Classes", DuctalesConstants.DEFAULT_STARDIST_CLASSIFICATION);

			parameterPane.addSeparator("Cell delineation");
			parameterPane.addDoubleTextField("cellThickness", "Thickness (um)", DuctalesConstants.DEFAULT_CELL_THICKNESS);

			parameterPane.addSeparator("Other parameters");
			parameterPane.addCheckbox("useSelected", "Detect in selected annotation", false);

			parameterPane.addButton("Detect", e -> onDetectButtonClicked(e), true);

			configDialog.setScene(new Scene(parameterPane));
		}else 
			configDialog.requestFocus();

		configDialog.sizeToScene();
		configDialog.show();
	}

	private String[] getChannelNames() {
		var nChannels = qupath.getImageData().getServer().nChannels();
		var names = new String[nChannels];
		for(var i = 0; i < nChannels; ++i){
			names[i] = qupath.getImageData().getServer().getChannel(i).getName();
		}

		return names;
	}

	private void onDetectButtonClicked(MouseEvent event){
		parameterPane.saveParametersInCache();

		var curImage = qupath.getImageData();
		var cellThickness = (double)parameterPane.getParameters().get("cellThickness");

		var useSelected = (boolean)parameterPane.getParameters().get("useSelected");
		ROI detectionROI = null;
		if(useSelected) {
			detectionROI = curImage.getHierarchy().getSelectionModel().getSelectedROI();
			if(detectionROI == null) {
				Dialogs.showErrorMessage("Error", "No selected annotation");
				return;
			}
		}

		var detectedCells = new CellsDetector((String)parameterPane.getParameters().get("modelPath"))
				.threshold((double)parameterPane.getParameters().get("threshold"))
				.normalizePercentiles((double)parameterPane.getParameters().get("normalizePercMin"), (double)parameterPane.getParameters().get("normalizePercMax"))
				.tileSize((int)parameterPane.getParameters().get("tileSize"))
				.tileOverlap((int)parameterPane.getParameters().get("tileOverlap"))
				.channels((int[])parameterPane.getParameters().get("channels"))
				.cellThickness(cellThickness)
				.classification((String[])parameterPane.getParameters().get("classes"))
				.detect(curImage, detectionROI);

		curImage.getHierarchy().addPathObjects(detectedCells);
	}
}
