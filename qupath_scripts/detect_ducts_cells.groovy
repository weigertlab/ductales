import qupath.ext.ductales.TissueFinder
import qupath.ext.ductales.DuctRegionsFinder
import qupath.ext.ductales.CellsDetector
import qupath.ext.ductales.CellsInfoExtractor
import qupath.lib.color.StainVector.DefaultStains
import ij.process.AutoThresholder

modelPath = "C:\\Users\\quentin.juppet\\Desktop\\Ductales\\models\\stardist with duct and species class v4.pb"
channel_indices = new int[]{0, 1, 2}
classes = new String[]{"No Duct", "Duct - Mouse", "Duct - Human"}

// Duct regions are an estimation to speed up cell detection
showTissue = true
showDuctRegions = true
measureCellsFeatures = true

image = getCurrentImageData()

if(showTissue){
	print("Detecting tissue...")

	tissue = new TissueFinder()
		.downsample(8.0)
		.closeSize(30)
		.openSize(15)
		.find(image)
	
	image.getHierarchy().addPathObject(ductRegions)
}

print("Detecting duct regions...")

ductRegions = new DuctRegionsFinder()
	.deconvolutionStain(DefaultStains.HEMATOXYLIN)
	.downsample(8.0)
	.gaussianSigma(2.0)
	.thresholdMethod(AutoThresholder.Method.Triangle)
	.minArea(100)
	.dilatation(50)
	.find(image)

if(showDuctRegions){
	image.getHierarchy().addPathObject(ductRegions);
}

print("Detecting cells...")

detectedCells = new CellsDetector(modelPath)
	.threshold(0.5)
	.normalize(true)
	.tileSize(512)
	.tileOverlap(64)
	.channels(channel_indices)
	.cellThickness(2)
	.classification(classes)
	.detect(image, ductRegions.getROI())

image.getHierarchy().addPathObjects(detectedCells)

if(measureCellsFeatures){
	print("Measuring cells features...")

	new CellsInfoExtractor()
			.measureShape(true)
			.measureIntensity(true)
			.measureTexture(false)
			.extract(image, cells);
}
