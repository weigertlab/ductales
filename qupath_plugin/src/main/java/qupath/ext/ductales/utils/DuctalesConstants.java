package qupath.ext.ductales.utils;

import ij.process.AutoThresholder;
import qupath.lib.color.StainVector.DefaultStains;

public class DuctalesConstants {
	public static String PARAMETERS_CACHE_FILE = "./parameters_cache.json";

	public static String[] PIXEL_UNITS = {"px", "um"};

	public static double DEFAULT_FIND_DUCT_DOWNSAMPLE = 8;
	public static int DEFAULT_FIND_DUCT_DECONVOLUTION_STAIN_INDEX = DefaultStains.EOSIN.ordinal();
	public static int DEFAULT_FIND_DUCT_THRESHOLDING_METHOD_INDEX = AutoThresholder.Method.Default.ordinal();
	public static int DEFAULT_FIND_DUCT_MIN_AREA = 100;
	public static double DEFAULT_FIND_DUCT_DILATATION = 50;
	public static double DEFAULT_FIND_DUCT_GAUSSIAN_SIGMA = 2;
	
	public static boolean DEFAULT_CELL_MEASURE_SHAPE = true;
	public static boolean DEFAULT_CELL_MEASURE_INTENSITY = true;
	public static boolean DEFAULT_CELL_MEASURE_TEXTURE = true;

	public static double DEFAULT_STARDIST_NORMALIZE_PERCENTILE_MIN = 1;
	public static double DEFAULT_STARDIST_NORMALIZE_PERCENTILE_MAX = 99;
	public static int DEFAULT_STARDIST_TILE_SIZE = 512;
	public static int DEFAULT_STARDIST_TILE_OVERLAP = 64;
	public static String[] DEFAULT_STARDIST_CLASSIFICATION = {"No Duct", "Duct - Mouse", "Duct - Human"};
	public static double DEFAULT_CELL_THICKNESS = 2.0;
	public static int DEFAULT_CELL_THICKNESS_UNIT_INDEX = 1;
	public static int[] DEFAULT_STARDIST_CHANNELS = {0, 1, 2};
	public static double DEFAULT_STARDIST_THRESHOLD = 0.5;
	
	public static String[] DEFAULT_NO_DUCT_CLASSES = {"No Duct"};
	public static String[] DEFAULT_DUCT_CLASSES = {"Duct - Mouse", "Duct - Human"};
	public static double DEFAULT_DUCT_STRUCTURE_MAX_DISTANCE = 50;
	public static int DEFAULT_DUCT_STRUCTURE_MIN_CELL_SIZE = 10;
	public static boolean DEFAULT_DUCT_MEASURE = true;
	public static double[] DEFAULT_DUCT_HOLES_MIN_DISTANCES = {10., 20., 30., 50.};
	public static int DEFAULT_DUCT_HOLES_MIN_CELL_SIZE = 5;
	public static boolean DEFAULT_REFINE_BOUNDARIES = true;
	public static double DEFAULT_TRIANGLE_TO_REFINE_MIN_ANGLE = 120;
	public static boolean DEFAULT_SHOW_HOLES = false;
	public static boolean DEFAULT_SHOW_PERIMETERS = false;
	public static boolean DEFAULT_SHOW_DELAUNAY = false;
}
