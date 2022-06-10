package qupath.ext.ductales;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.ops.ImageOps.Normalize;

public class CellsDetector {
	private final static Logger logger = LoggerFactory.getLogger(CellsDetector.class);

	private Object builder;
	private boolean normalize = false;
	private boolean normalizePercentiles = false;
	private double normalizePercMin;
	private double normalizePercMax;

	public CellsDetector(String modelPath) {
		try {
			var clsStardist = Class.forName("qupath.ext.stardist.StarDist2D");
			var builderMethod = clsStardist.getMethod("builder", String.class);
			builder = builderMethod.invoke(null, modelPath);
			var pixelSizeMethod = builder.getClass().getMethod("pixelSize", double.class);
			builder = pixelSizeMethod.invoke(builder, 0.5);
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}

		// Set default values
		threshold(DuctalesConstants.DEFAULT_STARDIST_THRESHOLD);
		normalize(true);
		tileSize(DuctalesConstants.DEFAULT_STARDIST_TILE_SIZE);
		tileOverlap(DuctalesConstants.DEFAULT_STARDIST_TILE_OVERLAP);
		channels(DuctalesConstants.DEFAULT_STARDIST_CHANNELS);
		cellThickness(DuctalesConstants.DEFAULT_CELL_THICKNESS);
		classification(DuctalesConstants.DEFAULT_STARDIST_CLASSIFICATION);
	}

	public CellsDetector threshold(double threshold) {
		try {
			var thresholdMethod = builder.getClass().getMethod("threshold", double.class);
			builder = thresholdMethod.invoke(builder, threshold);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector normalize(boolean normalize) {
		try {
			this.normalize = normalize;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector normalizePercentiles(double normalizePercMin, double normalizePercMax) {
		try {
			normalizePercentiles = true;
			this.normalizePercMin = normalizePercMin;
			this.normalizePercMax = normalizePercMax;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector tileSize(int tileSize) {
		try {
			var tileSizeMethod = builder.getClass().getMethod("tileSize", int.class);
			builder = tileSizeMethod.invoke(builder, tileSize);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector tileOverlap(int tileOverlap) {
		try {
			var paddingMethod = builder.getClass().getMethod("padding", int.class);
			builder = paddingMethod.invoke(builder, tileOverlap);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector channels(int[] channels) {
		try {
			var channelsMethod = builder.getClass().getMethod("channels", int[].class);
			builder = channelsMethod.invoke(builder, channels);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector cellThickness(double cellThickness) {
		try {
			// cell thickness in pixel
			var cellExpansionMethod = builder.getClass().getMethod("cellExpansion", double.class);
			builder = cellExpansionMethod.invoke(builder, cellThickness);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public CellsDetector classification(String[] classifications) {
		try {
			Map<Integer, String> class_map = new HashMap<Integer, String>();
			class_map.put(0, "Background");
			for(var i = 0; i < classifications.length; ++i)
				class_map.put(i+1, classifications[i]);
			var classificationNamesMethod = builder.getClass().getMethod("classificationNames", Map.class);
			builder = classificationNamesMethod.invoke(builder, class_map);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	public List<PathObject> detect(ImageData<BufferedImage> image, ROI detectionROI) {
		try {
			if(normalize) {
				if(normalizePercentiles) {
					var normalizeMethod = builder.getClass().getMethod("normalizePercentiles", double.class, double.class);
					builder = normalizeMethod.invoke(builder, normalizePercMin, normalizePercMax);
				} else {
					var minVal = image.getServer().getPixelType().getLowerBound().doubleValue();
					var maxVal = image.getServer().getPixelType().getUpperBound().doubleValue();
					var addMethod = builder.getClass().getMethod("inputAdd", (new double[1]).getClass());
					builder = addMethod.invoke(builder, new Object[] {new double[] {-minVal}});
					var preprocessMethod = builder.getClass().getMethod("preprocess", (new ImageOp[1]).getClass());
					var divideOps = ImageOps.Core.divide(maxVal - minVal);
					builder = preprocessMethod.invoke(builder, new Object[] {new ImageOp[] {divideOps}});
				}
			}
			
			if(detectionROI == null) {
				detectionROI = getFullImageROI(image.getServer());
			}
			var buildMethod = builder.getClass().getMethod("build");
			var model = buildMethod.invoke(builder);

			var clsStardist = Class.forName("qupath.ext.stardist.StarDist2D");
			var detectMethod = clsStardist.getMethod("detectObjects", ImageData.class, ROI.class);
			var detectedCells = (List<PathObject>)detectMethod.invoke(model, image, detectionROI);
			return detectedCells;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Detect cells", e);
		}
	}

	private ROI getFullImageROI(ImageServer<BufferedImage> server) {
		var plane = ImagePlane.getPlane(0, 0);
		var width = server.getWidth();
		var height = server.getHeight();
		return ROIs.createRectangleROI(0, 0, width, height, plane);
	}

}
