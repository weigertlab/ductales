package qupath.ext.ductales.commands;

import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.ext.ductales.TissueFinder;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.ext.ductales.utils.ParameterPane;
import qupath.lib.gui.QuPathGUI;

public class FindTissueCommand implements Runnable {
	private QuPathGUI qupath;
	private Stage configDialog;
	private ParameterPane parameterPane;

	public FindTissueCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if(configDialog == null) {
			configDialog = new Stage();
			configDialog.setResizable(false);
			configDialog.setTitle("Find tissue...");
			configDialog.setOnCloseRequest(e -> configDialog = null);
			configDialog.initOwner(qupath.getStage());

			parameterPane = new ParameterPane();
			parameterPane.addDoubleTextField("downsample", "Downsample", DuctalesConstants.DEFAULT_FIND_TISSUE_DOWNSAMPLE);
			parameterPane.addIntegerTextField("closeSize", "Close size", DuctalesConstants.DEFAULT_FIND_TISSUE_CLOSE_SIZE);
			parameterPane.addIntegerTextField("openSize", "Open size", DuctalesConstants.DEFAULT_FIND_TISSUE_OPEN_SIZE);

			parameterPane.addButton("Find", e -> onFindRegionButtonClicked(e), true);

			configDialog.setScene(new Scene(parameterPane));
		}else 
			configDialog.requestFocus();

		configDialog.sizeToScene();
		configDialog.show();
	}

	private void onFindRegionButtonClicked(MouseEvent event){
		parameterPane.saveParametersInCache();

		var curImage = qupath.getImageData();

		var annotation = new TissueFinder()
				.downsample((double)parameterPane.getParameters().get("downsample"))
				.closeSize((int)parameterPane.getParameters().get("closeSize"))
				.openSize((int)parameterPane.getParameters().get("openSize"))
				.find(curImage);

		curImage.getHierarchy().addPathObject(annotation);
	}
}
