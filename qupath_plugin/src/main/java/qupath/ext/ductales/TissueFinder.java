package qupath.ext.ductales;

import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.plugin.filter.RankFilters;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.color.StainVector.DefaultStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;

public class TissueFinder {
	private final static Logger logger = LoggerFactory.getLogger(TissueFinder.class);

	private double downsample = DuctalesConstants.DEFAULT_FIND_TISSUE_DOWNSAMPLE;
	private int closeSize = DuctalesConstants.DEFAULT_FIND_TISSUE_CLOSE_SIZE;
	private int openSize = DuctalesConstants.DEFAULT_FIND_TISSUE_OPEN_SIZE;


	public TissueFinder downsample(double downsample) {
		this.downsample = downsample;
		return this;
	}
	
	public TissueFinder closeSize(int closeSize) {
		this.closeSize = closeSize;
		return this;
	}
	
	public TissueFinder openSize(int openSize) {
		this.openSize = openSize;
		return this;
	}

	public PathObject find(ImageData<BufferedImage> image) {
		try {
			var eosinStain = StainVector.makeDefaultStainVector(DefaultStains.EOSIN);
			var hematoxylinStain = StainVector.makeDefaultStainVector(DefaultStains.HEMATOXYLIN);
			
			var colorDeconvolutionStains = new ColorDeconvolutionStains("Color deconv", hematoxylinStain, eosinStain, 255, 255, 255);
			
			var eosinServer = new TransformedServerBuilder(image.getServer()).deconvolveStains(colorDeconvolutionStains, 2).build();
			
			var imp = IJTools.convertToImagePlus(eosinServer, RegionRequest.createInstance(eosinServer, downsample)).getImage();
			
			imp.getProcessor().setAutoThreshold(AutoThresholder.Method.Mean, true);
			var mask = imp.createThresholdMask();
			
			new RankFilters().rank(mask, closeSize, RankFilters.CLOSE);
			new RankFilters().rank(mask, openSize, RankFilters.OPEN);
			
			RoiLabeling.fillHoles(mask);

			mask.threshold(128);
			var ijroi = new ThresholdToSelection().convert(mask);
			
			var roi = IJTools.convertToROI(ijroi, imp.getCalibration(), downsample, ImagePlane.getPlane(0, 0));

			var annotation = PathObjects.createAnnotationObject(roi);
			annotation.setPathClass(PathClassFactory.getPathClass("Tissue estimation"));
			ObjectMeasurements.addShapeMeasurements(annotation, image.getServer().getPixelCalibration());

			return annotation;
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Find tissue", e);
		}
	}
	
}
