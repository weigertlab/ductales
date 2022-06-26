import qupath.ext.ductales.TissueFinder
import qupath.ext.ductales.DuctRegionsFinder
import qupath.ext.ductales.CellsDetector
import qupath.ext.ductales.CellsInfoExtractor
import qupath.ext.ductales.DuctStructureComputer
import qupath.lib.color.StainVector.DefaultStains
import ij.process.AutoThresholder

modelPath = "C:\\Users\\quentin.juppet\\Desktop\\Ductales\\models\\stardist with duct and species class v4.pb"

channelIndices = new int[]{0, 1, 2}
classes = new String[]{"No Duct", "Duct - Mouse", "Duct - Human"}
excludedClasses = new String[]{"No Duct"}
ductClasses = new String[]{"Duct - Mouse", "Duct - Human"}

// Duct regions are an estimation to speed up cell detection
showTissue = true
showDuctRegions = true
showHoles = true
showPerimeters = true
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
    image.getHierarchy().addPathObject(ductRegions)
}

print("Detecting cells...")

cells = new CellsDetector(modelPath)
    .threshold(0.5)
    .normalize(true)
    .tileSize(512)
    .tileOverlap(64)
    .channels(channelIndices)
    .cellThickness(2)
    .classification(classes)
    .detect(image, ductRegions.getROI())

image.getHierarchy().addPathObjects(cells)

if(measureCellsFeatures){
	print("Measuring cells features...")

	new CellsInfoExtractor()
			.measureShape(true)
			.measureIntensity(true)
			.measureTexture(false)
			.extract(image, cells);
}

print("Computing ducts...")

ductComputer = new DuctStructureComputer()
    .excludeClasses(excludedClasses)
    .ductMaxDistance(50)
    .ductMinCellSize(10)
    .measure(true)
    .ductClasses(ductClasses)
    .holesMinDistances(new double[]{10, 20, 30, 50})
    .holesMinCellSize(5)
    .refineBoundaries(true)
    .triangleToRefineMinAngle(120)
ducts = ductComputer.compute(image, cells)
	
image.getHierarchy().addPathObjects(ducts)

if(showHoles) {
    holes = ductComputer.getHolesPathObjects(image)
    image.getHierarchy().addPathObjects(holes)
}
if(showPerimeters) {
    perimeters = ductComputer.getPerimetersPathObjects(image)
    image.getHierarchy().addPathObjects(perimeters)
}
ductComputer.addParentRelations(ducts)
