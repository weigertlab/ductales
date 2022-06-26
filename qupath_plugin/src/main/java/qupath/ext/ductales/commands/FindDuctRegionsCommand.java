package qupath.ext.ductales.commands;

import ij.process.AutoThresholder;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.ext.ductales.DuctRegionsFinder;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.ext.ductales.utils.ParameterPane;
import qupath.lib.color.StainVector.DefaultStains;
import qupath.lib.gui.QuPathGUI;

public class FindDuctRegionsCommand implements Runnable {
	private QuPathGUI qupath;
	private Stage configDialog;
	private ParameterPane parameterPane;

	public FindDuctRegionsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if(configDialog == null) {
			configDialog = new Stage();
			configDialog.setResizable(false);
			configDialog.setTitle("Find duct regions...");
			configDialog.setOnCloseRequest(e -> configDialog = null);
			configDialog.initOwner(qupath.getStage());

			parameterPane = new ParameterPane();
			parameterPane.addDoubleTextField("downsample", "Downsample", DuctalesConstants.DEFAULT_FIND_DUCT_DOWNSAMPLE);
			parameterPane.addSelectionComboBox("deconvolutionStain", "Deconvolution stain", DuctalesConstants.DEFAULT_FIND_DUCT_DECONVOLUTION_STAIN_INDEX, getDeconvolutionStains());
			parameterPane.addDoubleTextField("gaussianSigma", "Gaussian sigma", DuctalesConstants.DEFAULT_FIND_DUCT_GAUSSIAN_SIGMA);
			parameterPane.addSelectionComboBox("thresholdMethod", "Threshold method", DuctalesConstants.DEFAULT_FIND_DUCT_THRESHOLDING_METHOD_INDEX, AutoThresholder.getMethods());
			parameterPane.addIntegerTextField("minArea", "Minimum area", DuctalesConstants.DEFAULT_FIND_DUCT_MIN_AREA);
			parameterPane.addDoubleTextField("dilatation", "Dilatation", DuctalesConstants.DEFAULT_FIND_DUCT_DILATATION);

			parameterPane.addButton("Find regions", e -> onFindRegionButtonClicked(e), true);

			configDialog.setScene(new Scene(parameterPane));
		}else 
			configDialog.requestFocus();

		configDialog.sizeToScene();
		configDialog.show();
	}

	private String[] getDeconvolutionStains() {
		var deconvolutionStains = new String[DuctalesConstants.H_E_STAINS.length];
		for(int i = 0; i < DuctalesConstants.H_E_STAINS.length; ++i) {
			deconvolutionStains[i] = DuctalesConstants.H_E_STAINS[i].toString();
		}
		return deconvolutionStains;
	}

	private void onFindRegionButtonClicked(MouseEvent event){
		parameterPane.saveParametersInCache();

		var curImage = qupath.getImageData();

		var annotation = new DuctRegionsFinder()
				.deconvolutionStain(DefaultStains.values()[(int)parameterPane.getParameters().get("deconvolutionStain")])
				.downsample((double)parameterPane.getParameters().get("downsample"))
				.gaussianSigma((double)parameterPane.getParameters().get("gaussianSigma"))
				.thresholdMethod(AutoThresholder.Method.values()[(int)parameterPane.getParameters().get("thresholdMethod")])
				.minArea((int)parameterPane.getParameters().get("minArea"))
				.dilatation((double)parameterPane.getParameters().get("dilatation"))
				.find(curImage);

		curImage.getHierarchy().addPathObject(annotation);
	}
}
