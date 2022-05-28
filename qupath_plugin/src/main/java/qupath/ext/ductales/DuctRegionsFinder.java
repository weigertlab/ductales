package qupath.ext.ductales;

import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.color.StainVector.DefaultStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;

public class DuctRegionsFinder {
	private final static Logger logger = LoggerFactory.getLogger(DuctRegionsFinder.class);

	private DefaultStains stainVector = DefaultStains.values()[DuctalesConstants.DEFAULT_FIND_DUCT_DECONVOLUTION_STAIN_INDEX];
	private double downsample = DuctalesConstants.DEFAULT_FIND_DUCT_DOWNSAMPLE;
	private double gaussianSigma = DuctalesConstants.DEFAULT_FIND_DUCT_GAUSSIAN_SIGMA;
	private AutoThresholder.Method thresholdMethod = AutoThresholder.Method.values()[DuctalesConstants.DEFAULT_FIND_DUCT_THRESHOLDING_METHOD_INDEX];
	private int minArea = DuctalesConstants.DEFAULT_FIND_DUCT_MIN_AREA;
	private double dilatation = DuctalesConstants.DEFAULT_FIND_DUCT_DILATATION;


	public DuctRegionsFinder deconvolutionStain(DefaultStains deconvolutionStain) {
		this.stainVector = deconvolutionStain;
		return this;
	}

	public DuctRegionsFinder downsample(double downsample) {
		this.downsample = downsample;
		return this;
	}

	public DuctRegionsFinder gaussianSigma(double gaussianSigma) {
		this.gaussianSigma = gaussianSigma;
		return this;
	}

	public DuctRegionsFinder thresholdMethod(AutoThresholder.Method thresholdMethod) {
		this.thresholdMethod = thresholdMethod;
		return this;
	}

	public DuctRegionsFinder minArea(int minArea) {
		this.minArea = minArea;
		return this;
	}

	public DuctRegionsFinder dilatation(double dilatation) {
		this.dilatation = dilatation;
		return this;
	}

	public PathObject find(ImageData<BufferedImage> image) {
		try {
			var deconvolutionStain = StainVector.makeDefaultStainVector(stainVector);
			var colorDeconvolutionStains = new ColorDeconvolutionStains("Color deconv", deconvolutionStain, StainVector.createStainVector("Red", 1, 0, 0), 255, 255, 255);
			var deconvolvedServer = new TransformedServerBuilder(image.getServer()).deconvolveStains(colorDeconvolutionStains, 1).build();

			var imp = IJTools.convertToImagePlus(deconvolvedServer, RegionRequest.createInstance(deconvolvedServer, downsample)).getImage();
			new GaussianBlur().blurGaussian(imp.getProcessor(), gaussianSigma);

			imp.getProcessor().setAutoThreshold(thresholdMethod, true);

			var mask = imp.createThresholdMask();

			RoiLabeling.removeSmallAreas(mask, minArea, false);

			mask.threshold(128);
			var ijroi = new ThresholdToSelection().convert(mask);

			var roi = IJTools.convertToROI(ijroi, imp.getCalibration(), downsample, ImagePlane.getPlane(0, 0));

			roi = GeometryTools.geometryToROI(roi.getGeometry().buffer(dilatation), ImagePlane.getPlane(0, 0));

			roi = RoiTools.fillHoles(roi);

			var annotation = PathObjects.createAnnotationObject(roi);

			return annotation;
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Find duct regions", e);
		}
	}

}
