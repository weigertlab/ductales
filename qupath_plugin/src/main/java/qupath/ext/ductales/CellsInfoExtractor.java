package qupath.ext.ductales;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.lib.analysis.features.HaralickFeatureComputer;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.analysis.features.ObjectMeasurements.Compartments;
import qupath.lib.analysis.features.ObjectMeasurements.Measurements;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.color.StainVector.DefaultStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.objects.PathCellObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.opencv.tools.OpenCVTools;

public class CellsInfoExtractor {
	private final static Logger logger = LoggerFactory.getLogger(CellsInfoExtractor.class);
	private boolean measureShape;
	private boolean measureIntensity;
	private boolean measureTexture;


	public CellsInfoExtractor() {
		// Set default values
		measureShape(DuctalesConstants.DEFAULT_CELL_MEASURE_SHAPE);
		measureIntensity(DuctalesConstants.DEFAULT_CELL_MEASURE_INTENSITY);
		measureTexture(DuctalesConstants.DEFAULT_CELL_MEASURE_TEXTURE);
	}

	public CellsInfoExtractor measureShape(boolean measureShape) {
		try {
			this.measureShape = measureShape;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Measure cells infos", e);
		}
	}

	public CellsInfoExtractor measureIntensity(boolean measureIntensity) {
		try {
			this.measureIntensity = measureIntensity;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Measure cells infos", e);
		}
	}

	public CellsInfoExtractor measureTexture(boolean measureTexture) {
		try {
			this.measureTexture = measureTexture;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Measure cells infos", e);
		}
	}


	public void extract(ImageData<BufferedImage> image, Collection<PathCellObject> cells) {
		if(measureShape) {
			cells.parallelStream().forEach(c -> {
				try {
					ObjectMeasurements.addShapeMeasurements(c, image.getServer().getPixelCalibration());
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
					throw new RuntimeException("Unable to run command: Measure cells infos", e);
				}
			});
		}
		if(measureIntensity) {
			var measurements = Arrays.asList(
					Measurements.MAX,
					Measurements.MEAN,
					Measurements.MEDIAN,
					Measurements.MIN,
					Measurements.STD_DEV,
					Measurements.VARIANCE
					);
			var compartments = Arrays.asList(
					Compartments.NUCLEUS,
					Compartments.CYTOPLASM
					);

			cells.parallelStream().forEach(c -> {
				try {
					ObjectMeasurements.addIntensityMeasurements(image.getServer(), c, 1, measurements, compartments);
				} catch (IOException e) {
					logger.error(e.getLocalizedMessage(), e);
					throw new RuntimeException("Unable to run command: Measure cells infos", e);
				}
			});
			
			var eosinStain = StainVector.makeDefaultStainVector(DefaultStains.EOSIN);
			var hematoxylinStain = StainVector.makeDefaultStainVector(DefaultStains.HEMATOXYLIN);
			
			var colorDeconvolutionStains = new ColorDeconvolutionStains("Color deconv", hematoxylinStain, eosinStain, 255, 255, 255);
			var deconvolvedServer = new TransformedServerBuilder(image.getServer()).deconvolveStains(colorDeconvolutionStains, 1, 2).build();

			cells.parallelStream().forEach(c -> {
				try {
					ObjectMeasurements.addIntensityMeasurements(deconvolvedServer, c, 1, measurements, compartments);
				} catch (IOException e) {
					logger.error(e.getLocalizedMessage(), e);
					throw new RuntimeException("Unable to run command: Measure cells infos", e);
				}
			});
		}
		if(measureTexture) {
			computeHaralickFeatures(image.getServer(), cells, Compartments.NUCLEUS);
			computeHaralickFeatures(image.getServer(), cells, Compartments.CYTOPLASM);
		}
	}

	private void computeHaralickFeatures(ImageServer<BufferedImage> server, Collection<PathCellObject> cells, Compartments compartment) {
		assert(compartment == Compartments.NUCLEUS || compartment == Compartments.CYTOPLASM);

		var eosinStain = StainVector.makeDefaultStainVector(DefaultStains.EOSIN);
		var hematoxylinStain = StainVector.makeDefaultStainVector(DefaultStains.HEMATOXYLIN);
		
		var colorDeconvolutionStains = new ColorDeconvolutionStains("Color deconv", hematoxylinStain, eosinStain, 255, 255, 255);

		ImageServer<BufferedImage> deconvolvedServer;
		if(compartment == Compartments.NUCLEUS)
			// Use hematoxylin to compute haralick features for cytoplasm
			deconvolvedServer = new TransformedServerBuilder(server).deconvolveStains(colorDeconvolutionStains, 1).build();
		else
			// Use eosin to compute haralick features for cytoplasm
			deconvolvedServer = new TransformedServerBuilder(server).deconvolveStains(colorDeconvolutionStains, 2).build();

		cells.parallelStream().forEach(c -> {
			try {
				RegionRequest regionRequest;
				SimpleImage mask;
				if(compartment == Compartments.NUCLEUS) {
					regionRequest = RegionRequest.createInstance("nucleus roi", 1, c.getNucleusROI());
					mask = getNucleusMask(c, regionRequest);
				} else {
					regionRequest = RegionRequest.createInstance("cell roi", 1, c.getROI());
					mask = getCytoplasmMask(c, regionRequest);
				}

				var image = deconvolvedServer.readBufferedImage(regionRequest);
				var imageMat = OpenCVTools.imageToMat(image);
				var simpleImage = OpenCVTools.matToSimpleImage(imageMat, 0);

				var minVal = 0;//server.getPixelType().getLowerBound().doubleValue();
				var maxVal = 1;//server.getPixelType().getUpperBound().doubleValue();

				var features = HaralickFeatureComputer.measureHaralick(simpleImage, mask, 256, minVal, maxVal, 1);
				for (var i = 0; i < features.nFeatures(); ++i) {
					String measureName;
					if(compartment == Compartments.NUCLEUS)
						measureName = "Nucleus: " + features.getFeatureName(i);
					else
						measureName = "Cytoplasm: " + features.getFeatureName(i);
					c.getMeasurementList().addMeasurement(measureName, features.getFeature(i));
				}
			} catch (IOException e) {
				logger.error(e.getLocalizedMessage(), e);
				throw new RuntimeException("Unable to run command: Measure cells infos", e);
			}

		});
	}

	private SimpleImage getNucleusMask(PathCellObject cell, RegionRequest regionRequest) {
		var mask = BufferedImageTools.createROIMask(regionRequest.getWidth(), regionRequest.getHeight(), cell.getNucleusROI(), regionRequest.getX(), regionRequest.getY(), 1);
		var mat = OpenCVTools.imageToMat(mask);

		return OpenCVTools.matToSimpleImage(mat, 0);
	}

	private SimpleImage getCytoplasmMask(PathCellObject cell, RegionRequest regionRequest) {
		var cytoplasmROI = RoiTools.combineROIs(cell.getROI(), cell.getNucleusROI(), RoiTools.CombineOp.SUBTRACT);
		var mask = BufferedImageTools.createROIMask(regionRequest.getWidth(), regionRequest.getHeight(), cytoplasmROI, regionRequest.getX(), regionRequest.getY(), 1);
		var mat = OpenCVTools.imageToMat(mask);

		return OpenCVTools.matToSimpleImage(mat, 0);
	}
}
