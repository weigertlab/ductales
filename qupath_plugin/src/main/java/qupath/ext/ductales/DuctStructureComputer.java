package qupath.ext.ductales;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlay.OverlayOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.util.Pair;
import qupath.ext.ductales.utils.DuctalesConstants;
import qupath.lib.analysis.DelaunayTools;
import qupath.lib.analysis.DelaunayTools.Subdivision;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.objects.DefaultPathObjectConnectionGroup;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectConnectionGroup;
import qupath.lib.objects.PathObjectConnections;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;

public class DuctStructureComputer {
	private final static Logger logger = LoggerFactory.getLogger(DuctStructureComputer.class);
	private Set<PathClass> excludedClasses;
	private double ductMaxDistance;
	private int ductMinCellSize;
	private boolean measure;
	private Set<PathClass> ductClasses;
	private double[] holesMinDistances;
	private int holesMinCellSize;
	private boolean refineBoundaries;
	private double triangleToRefineMinAngle;

	private Collection<Boundary> currentHoles = new ArrayList<>();
	private Collection<Boundary> currentPerimeters = new ArrayList<>();
	private PathObjectConnectionGroup currentConnexions;
	private Lock lock = new ReentrantLock();

	private static final String DISTANCE_TO_BOUNDARIES = "Distance to boundaries";
	private static final String IS_IN_MONOLAYER = "Is in monolayer";
	private static final Integer HOLES_COLOR = ColorToolsAwt.getCachedColor(0, 0, 255).getRGB();
	private static final Integer PERIMETERS_COLOR = ColorToolsAwt.getCachedColor(255, 128, 0).getRGB();

	public DuctStructureComputer() {
		// Set default values
		excludeClasses(DuctalesConstants.DEFAULT_NO_DUCT_CLASSES);
		ductMaxDistance(DuctalesConstants.DEFAULT_DUCT_STRUCTURE_MAX_DISTANCE);
		ductMinCellSize(DuctalesConstants.DEFAULT_DUCT_STRUCTURE_MIN_CELL_SIZE);
		measure(DuctalesConstants.DEFAULT_DUCT_MEASURE);
		ductClasses(DuctalesConstants.DEFAULT_DUCT_CLASSES);
		holesMinDistances(DuctalesConstants.DEFAULT_DUCT_HOLES_MIN_DISTANCES);
		holesMinCellSize(DuctalesConstants.DEFAULT_DUCT_HOLES_MIN_CELL_SIZE);
		refineBoundaries(DuctalesConstants.DEFAULT_REFINE_BOUNDARIES);
		triangleToRefineMinAngle(DuctalesConstants.DEFAULT_TRIANGLE_TO_REFINE_MIN_ANGLE);
	}

	public DuctStructureComputer excludeClasses(String[] classes) {
		try {
			excludedClasses = new HashSet<>();
			for (String class_ : classes) {
				excludedClasses.add(PathClassFactory.getPathClass(class_));
			}
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer ductMaxDistance(double distance) {
		try {
			ductMaxDistance = distance;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer ductMinCellSize(int minCellSize) {
		try {
			ductMinCellSize = minCellSize;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer measure(boolean measure) {
		try {
			this.measure = measure;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer ductClasses(String[] classes) {
		try {
			ductClasses = new HashSet<>();
			for (String class_ : classes) {
				ductClasses.add(PathClassFactory.getPathClass(class_));
			}			
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer holesMinDistances(double[] distances) {
		try {
			holesMinDistances = distances;
			Arrays.sort(holesMinDistances);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer holesMinCellSize(int minCellSize) {
		try {
			holesMinCellSize = minCellSize;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public DuctStructureComputer refineBoundaries(boolean refineHoles) {
		try {
			this.refineBoundaries = refineHoles;
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	// Expect angle in degree
	public DuctStructureComputer triangleToRefineMinAngle(double angle) {
		try {
			triangleToRefineMinAngle = Math.toRadians(angle);
			return this;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	class DelaunayConnectionGroup implements PathObjectConnectionGroup {
		private Map<PathObject, List<PathObject>> neighbors;

		public DelaunayConnectionGroup(Map<PathObject, List<PathObject>> neighbors) {
			this.neighbors = neighbors;
		}

		@Override
		public boolean containsObject(PathObject pathObject) {
			return neighbors.containsKey(pathObject);
		}

		@Override
		public List<PathObject> getConnectedObjects(PathObject pathObject) {
			return neighbors.getOrDefault(pathObject, new ArrayList<PathObject>());
		}

		@Override
		public Collection<PathObject> getPathObjects() {
			return neighbors.keySet();
		}
	}

	public Collection<PathObject> compute(ImageData<BufferedImage> image, Collection<? extends PathObject> cells){
		try {
			lock.lock();
			try{
				currentHoles.clear();
				currentPerimeters.clear();
				currentConnexions = null;
			}finally{
				lock.unlock();
			}
			// Filter by class
			var filteredCells = (Collection<PathObject>)cells.stream().filter((cell) -> {
				return !excludedClasses.contains(cell.getPathClass());
			}).collect(Collectors.toList());
			// Compute delaunay
			var subdivision = DelaunayTools.createFromCentroids(filteredCells, true);
			// Get the clusters by distance
			var clusters = subdivision.getClusters(DelaunayTools.boundaryDistancePredicate(ductMaxDistance, true));

			var ducts = clusters.stream().filter(clusterChildren->{
				return clusterChildren.size() >= ductMinCellSize;
			}).map(clusterChildren->{
				var geos = clusterChildren.stream().map((child)->{
					var geo = child.getROI().getGeometry();
					geo = geo.convexHull(); // Required to avoid error when union (two shells found sometimes)
					return geo;
				}).collect(Collectors.toList());
				var geo = GeometryTools.getDefaultFactory().buildGeometry(geos).buffer(0);
				//var geo = GeometryTools.union(geos);
				var roi = GeometryTools.geometryToROI(geo, ImagePlane.getDefaultPlane());
				var annotation = PathObjects.createAnnotationObject(roi);
				annotation.addPathObjects(clusterChildren);
				return annotation;
			}).collect(Collectors.toList());

			if(measure) {
				measureDuctInfos(image, ducts, subdivision);
			}

			lock.lock();
			try{
				currentConnexions = new DelaunayConnectionGroup(subdivision.getFilteredNeighbors(DelaunayTools.boundaryDistancePredicate(ductMaxDistance, true)));
			}finally{
				lock.unlock();
			}

			return ducts;
		} catch(Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException("Unable to run command: Compute duct structure", e);
		}
	}

	public Collection<PathObject> getHolesPathObjects() {
		lock.lock();
		try {
			return currentHoles.stream().map(hole-> {
				var annotation = PathObjects.createAnnotationObject(hole.getROI());
				annotation.setColorRGB(HOLES_COLOR);
				return annotation;
			}).collect(Collectors.toList());
		} finally {
			lock.unlock();
		}
	}

	public Collection<PathObject> getPerimetersPathObjects() {
		lock.lock();
		try {
			return currentPerimeters.stream().map(perim-> {
				var annotation = PathObjects.createAnnotationObject(perim.getROI());
				annotation.setColorRGB(PERIMETERS_COLOR);
				return annotation;
			}).collect(Collectors.toList());
		} finally {
			lock.unlock();
		}
	}

	public void showDelaunay(ImageData<BufferedImage> image) {
		lock.lock();
		try{
			if (currentConnexions != null) {
				image.removeProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS);
				var connections = new PathObjectConnections();
				image.setProperty(DefaultPathObjectConnectionGroup.KEY_OBJECT_CONNECTIONS, connections);
				connections.addGroup(currentConnexions);
			}
		}finally{
			lock.unlock();
		}	
	}

	private void measureDuctInfos(ImageData<BufferedImage> image, Collection<PathObject> ducts, Subdivision subdivision) {
		var boundariesNeighbors = new ArrayList<Map<PathObject, List<PathObject>>>();
		for(Double holesMinDistance : holesMinDistances)
			boundariesNeighbors.add(subdivision.getFilteredNeighbors(DelaunayTools.boundaryDistancePredicate(holesMinDistance, true)));
		var ductNeighbors = subdivision.getFilteredNeighbors(DelaunayTools.boundaryDistancePredicate(ductMaxDistance, true));
		// Make sure we compute boundaries at maxDistance to have correct perimeter.
		if(holesMinDistances[holesMinDistances.length-1] != ductMaxDistance)
			boundariesNeighbors.add(ductNeighbors);
		
		ducts.parallelStream().forEach(d -> {
			try {
				ObjectMeasurements.addShapeMeasurements(d, image.getServer().getPixelCalibration());

				var cellDensity = d.getChildObjects().size() / d.getROI().getArea();
				d.getMeasurementList().putMeasurement("Cell density", cellDensity);

				for (PathClass pathClass : ductClasses) {
					var nbCells = d.getChildObjects().stream().filter(c->{
						return c.getPathClass() == pathClass;
					}).count();

					var cellDensityPerClass = nbCells / d.getROI().getArea();
					d.getMeasurementList().putMeasurement("Cell density - " + pathClass.getName(), cellDensityPerClass);
				}

				// Holes and perimeter
				var boundaries = findBoundaries(d.getChildObjects(), boundariesNeighbors);
				var holes = boundaries.stream().filter(b-> {
					return b.isHole;
				}).collect(Collectors.toList());
				var perimeters = boundaries.stream().filter(b-> {
					return !b.isHole;
				}).collect(Collectors.toList());
				
				d.getMeasurementList().putMeasurement("Number of holes", holes.size());
				
				var holesArea = holes.stream().map(h -> h.getROI().getArea()).reduce(0., (a,b) -> a+b);
				var perimetersArea = perimeters.stream().map(p -> p.getROI().getArea()).reduce(0., (a,b) -> a+b);
				var solidity = perimeters.stream().map(p -> p.getROI().getSolidity()).reduce(0., (a,b) -> a+b) / perimeters.size();

				d.getMeasurementList().putMeasurement("Porosity", holesArea / perimetersArea);
				d.getMeasurementList().putMeasurement("Perimeter solidity", solidity);

				computeCellDistanceToBoundaries(d.getChildObjects(), boundaries, ductNeighbors);

				var numberInMonolayer = 0;
				var meanDistanceToBorders = 0.0;
				var numberInLayer0 = 0;
				var numberInOtherLayers = 0;
				for(PathObject cell : d.getChildObjects()) {
					numberInMonolayer += cell.getMeasurementList().getMeasurementValue(IS_IN_MONOLAYER);
					var distToBoundaries = cell.getMeasurementList().getMeasurementValue(DISTANCE_TO_BOUNDARIES);
					meanDistanceToBorders += distToBoundaries;
					if(distToBoundaries == 0)
						numberInLayer0 += 1;
					else
						numberInOtherLayers += 1;
				}
				meanDistanceToBorders /= d.getChildObjects().size();
				d.getMeasurementList().putMeasurement("Number of monolayered cells", numberInMonolayer);
				d.getMeasurementList().putMeasurement("Mean cell distance to borders", meanDistanceToBorders);
				d.getMeasurementList().putMeasurement("Number of cells (layer=0)", numberInLayer0);
				d.getMeasurementList().putMeasurement("Number of cells (layer>0)", numberInOtherLayers);
				

				lock.lock();
				try{
					currentHoles.addAll(holes);
					currentPerimeters.addAll(perimeters);
				}finally{
					lock.unlock();
				}
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage(), e);
				throw new RuntimeException("Unable to run command: Compute duct structure", e);
			}
		});
	}

	class OrientedEdge{
		public PathObject start;
		public PathObject end;

		public OrientedEdge(PathObject start, PathObject end) {
			this.start = start;
			this.end = end;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof OrientedEdge))
				return false;
			var e = (OrientedEdge)obj;
			return start.equals(e.start) && end.equals(e.end);
		}

		@Override
		public int hashCode() {
			return start.hashCode() * end.hashCode();
		}

		public boolean isRight(PathObject cell) {
			var a = getNucleusCentroid(start);
			var b = getNucleusCentroid(end);
			var c = getNucleusCentroid(cell);

			return ((b.getX() - a.getX())*(c.getY() - a.getY()) 
					- (b.getY() - a.getY())*(c.getX() - a.getX())) < 0;
		}

		public OrientedEdge getOpposite() {
			return new OrientedEdge(end, start);
		}
	}

	class Boundary{
		public List<PathObject> cells;
		public boolean isHole;
		public Polygon polygon;

		public Boundary(List<PathObject> cells, boolean isHole) {
			this.cells = cells;
			this.isHole = isHole;
			var coords = new Coordinate[cells.size()+1];
			for(var i = 0; i < cells.size(); ++i) {
				var p = getNucleusCentroid(cells.get(i));
				coords[i] = new Coordinate(p.getX(), p.getY());
			}
			coords[cells.size()] = coords[0];
			polygon = new GeometryFactory().createPolygon(coords);
		}
		
		public PolygonROI getROI() {
			var points = cells.stream().map(cell -> {
				return getNucleusCentroid(cell);
			}).collect(Collectors.toList());
			return ROIs.createPolygonROI(points, ImagePlane.getDefaultPlane());
		}
	}

	private Collection<Boundary> findBoundaries(Collection<PathObject> ductCells, List<Map<PathObject, List<PathObject>>> boundariesNeighbors){
		List<Boundary> boundaries = new ArrayList<>();
		var cellSet = new HashSet<PathObject>(ductCells);
		for(var bni = 0; bni < boundariesNeighbors.size(); ++bni) {
			var keepPerimeters = bni == boundariesNeighbors.size()-1;
			var boundNeighbors = boundariesNeighbors.get(bni);
			// Compute graph edges
			Set<OrientedEdge> edges = new HashSet<>();
			Map<OrientedEdge, Boolean> isVisited = new HashMap<>();
			for (PathObject cell : ductCells) {
				var neighbors = boundNeighbors.get(cell);
				for (PathObject neighbor : neighbors) {
					if(!cellSet.contains(neighbor))
						continue;
					var edge1 = new OrientedEdge(cell, neighbor);
					var edge2 = new OrientedEdge(neighbor, cell);
					edges.add(edge1);
					edges.add(edge2);
					isVisited.put(edge1, false);
					isVisited.put(edge2, false);
				}
			}
			List<Boundary> curBoundaries = new ArrayList<>();
			Map<OrientedEdge, PathObject> edgesToRefine = new HashMap<>();
			for (OrientedEdge edge : edges) {
				if(isVisited.get(edge))
					continue;
				List<PathObject> curBoundary = new ArrayList<>();
				var startEdge = edge;
				var rightMostEdge = edge;
				do {
					edge = rightMostEdge;
					isVisited.put(edge, true);
					curBoundary.add(edge.end);
					rightMostEdge = null;

					var neighbors = boundNeighbors.get(edge.end);
					var isCurrentBestOnRightSide = false;
					for (PathObject neighbor : neighbors) {
						if(!cellSet.contains(neighbor))
							continue;
						if(neighbor.equals(edge.start))
							continue;
						var isRightMost = false;
						if(rightMostEdge == null)
							isRightMost = true;
						var isRightToEdge = edge.isRight(neighbor);
						if(rightMostEdge != null){
							var isRightToCurrentBest = rightMostEdge.isRight(neighbor);

							if(isCurrentBestOnRightSide) {
								// If neighbor is on right side to both current edge and right most neighboring edge, then it is the right most one.
								if(isRightToEdge && isRightToCurrentBest)
									isRightMost = true;
							}else {
								// If neighbor is on right side to both current edge or right most neighboring edge, then it is the right most one.
								if(isRightToEdge || isRightToCurrentBest)
									isRightMost = true;
							}
						}
						if(isRightMost) {
							rightMostEdge =  new OrientedEdge(edge.end, neighbor);
							isCurrentBestOnRightSide = isRightToEdge;	
						}
					}
					if(rightMostEdge == null)
						//If no right most edge, turn in other direction
						rightMostEdge = edge.getOpposite();
				}while(!startEdge.equals(rightMostEdge));
				
				var isHole = isPolygonClockwise(curBoundary);
				
				if(refineBoundaries) {
					if(curBoundary.size() == 3 && isHole)
					{
						var angles = getTriangleAngles(curBoundary);
						for(var i = 0; i < 3; ++i) {
							if(angles[i] >= triangleToRefineMinAngle) {
								var p1 = curBoundary.get((i+1) % 3);
								var p2 = curBoundary.get((i+2) % 3);
								edgesToRefine.put(new OrientedEdge(p2, p1), curBoundary.get(i));
								break;
							}
						}
					}
				}

				if(curBoundary.size() > 3)
					curBoundaries.add(new Boundary(curBoundary, isHole));
			}

			//curBoundaries = mergeAdjacentHoles(curBoundaries);

			if(refineBoundaries)
				curBoundaries = curBoundaries.stream().map(boundary -> {
					return refineBoundary(boundary, edgesToRefine);
				}).collect(Collectors.toList());

			curBoundaries = curBoundaries.stream().filter(boundary -> {
				return boundary.cells.size() >= holesMinCellSize;
			}).collect(Collectors.toList());
			
			curBoundaries.stream().forEach(boundary -> {
				boundary.cells.stream().forEach(cell -> {
					// Consider the cells detected at boundaries to be at distance 0
					cell.getMeasurementList().putMeasurement(DISTANCE_TO_BOUNDARIES, 0);					
				});
			});
			
			if(!keepPerimeters)
				curBoundaries = curBoundaries.stream().filter(boundary -> {
					return boundary.isHole;
				}).collect(Collectors.toList());
			
			curBoundaries = curBoundaries.stream().filter(boundary -> {
				if(!boundary.isHole)
					return true;
				// Existing hole are expected to cover other holes prediction for same points.
				return boundaries.stream().allMatch(boundary2 -> {
					return !boundary2.polygon.covers(boundary.polygon);
				});
			}).collect(Collectors.toList());

			
			boundaries.addAll(curBoundaries);
		}

		return boundaries;
	}

	private double[] getTriangleAngles(List<PathObject> triangle) {
		var p1 = getNucleusCentroid(triangle.get(0));
		var p2 = getNucleusCentroid(triangle.get(1));
		var p3 = getNucleusCentroid(triangle.get(2));

		var angles = new double[3];
		angles[0] = Math.abs(
				Math.atan2(p3.getY() - p1.getY(), p3.getX() - p1.getX()) -
				Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
		angles[1] = Math.abs(
				Math.atan2(p1.getY() - p2.getY(), p1.getX() - p2.getX()) -
				Math.atan2(p3.getY() - p2.getY(), p3.getX() - p2.getX()));
		if(angles[0] > Math.PI)
			angles[0] = 2*Math.PI - angles[0];
		if(angles[1] > Math.PI)
			angles[1] = 2*Math.PI - angles[1];
		angles[2] = Math.PI - angles[0] - angles[1];

		return angles;
	}

	private boolean isPolygonClockwise(List<PathObject> polygon) {
		var area = 0;
		for (var i = 0; i < polygon.size(); i++) {
			var j = (i + 1) % polygon.size();
			var pI = getNucleusCentroid(polygon.get(i));
			var pJ = getNucleusCentroid(polygon.get(j));
			area += pI.getX() * pJ.getY();
			area -= pJ.getX() * pI.getY();
		}
		return area < 0;
	}

	/*
	private List<Boundary> mergeAdjacentHoles(List<Boundary> boundaries){
		Map<OrientedEdge, Integer> edgeToBoundary = new HashMap<>();
		var isMerging = new boolean[boundaries.size()];
		List<Pair<Integer, Integer>> toMerge = new ArrayList<>();
		List<OrientedEdge> mergingEdge = new ArrayList<>();
		for(var i = 0; i < boundaries.size(); ++i) {
			if(!boundaries.get(i).isHole)
				continue;
			var polygonEdges = polygonPointsToEdge(boundaries.get(i).cells);
			for(var j = 0; j < polygonEdges.size(); ++j) {
				var edge = polygonEdges.get(j);
				edgeToBoundary.put(edge, i);
				var oppositeEdge = edge.getOpposite();
				if(edgeToBoundary.containsKey(oppositeEdge)) {
					var k = edgeToBoundary.get(oppositeEdge);
					toMerge.add(new Pair<Integer, Integer>(i, k));
					mergingEdge.add(edge);
					isMerging[i] = true;
					isMerging[k] = true;
				}
			}
		}

		var result = new ArrayList<Boundary>();
		Map<Integer, Integer> boundaryToMergedResult = new HashMap<Integer, Integer>();
		Map<Integer, Integer> replacedResult = new HashMap<Integer, Integer>();
		for(var i = 0; i < toMerge.size(); ++i) {
			var bInd1 = toMerge.get(i).getKey();
			var bInd2 = toMerge.get(i).getValue();
			var b1 = boundaries.get(bInd1);
			var b2 = boundaries.get(bInd2);
			int resInd1 = -1;
			int resInd2 = -1;

			if(boundaryToMergedResult.containsKey(bInd1)){
				resInd1 = boundaryToMergedResult.get(bInd1);
				while(replacedResult.containsKey(resInd1))
					resInd1 = replacedResult.get(resInd1);
				b1 = result.get(resInd1);
			}
			if(boundaryToMergedResult.containsKey(bInd2)) {
				resInd2 = boundaryToMergedResult.get(bInd2);
				while(replacedResult.containsKey(resInd2))
					resInd2 = replacedResult.get(resInd2);
				b2 = result.get(resInd2);
			}

			var edgeToMerge = mergingEdge.get(i);

			var mergedBoundary = new ArrayList<PathObject>();

			for(var j = 0; j < b1.cells.size(); ++j) {
				var cell = b1.cells.get(j);
				if(cell.equals(edgeToMerge.start)) {
					boolean startAdding = false;
					boolean metEndCell = false;
					for(var k = 0; k < b2.cells.size(); ++k) {
						var cell2 = b2.cells.get(k);
						if(cell2.equals(edgeToMerge.start))
							startAdding = true;
						else if(cell2.equals(edgeToMerge.end)) {
							metEndCell = true;
							break;
						}
						if(startAdding)
							mergedBoundary.add(cell2);
					}
					if(!metEndCell)
						for(var k = 0; k < b2.cells.size(); ++k) {
							var cell2 = b2.cells.get(k);
							if(cell2.equals(edgeToMerge.end))
								break;

							mergedBoundary.add(cell2);
						}
				} else {
					mergedBoundary.add(cell);
				}
			}
			result.add(new Boundary(mergedBoundary, true));
			boundaryToMergedResult.put(bInd1, i);
			boundaryToMergedResult.put(bInd2, i);
			if(resInd1 != -1)
				replacedResult.put(resInd1, i);
			if(resInd2 != -1)
				replacedResult.put(resInd2, i);
		}

		var finalResult = new ArrayList<Boundary>();
		for(var i = 0; i < result.size(); ++i) {
			if(!replacedResult.containsKey(i))
				finalResult.add(result.get(i));
		}

		for(var i = 0; i < boundaries.size(); ++i) {
			if(!isMerging[i])
				finalResult.add(boundaries.get(i));
		}

		return finalResult;
	}
	 */

	private Boundary refineBoundary(Boundary boundary, Map<OrientedEdge, PathObject> edgesToRefine){
		var polygonEdges = polygonPointsToEdge(boundary.cells);
		var curBoundary = new ArrayList<PathObject>();
		var hasRefined = false;
		for(var i = 0; i < polygonEdges.size(); ++i) {
			var edge = polygonEdges.get(i);
			if(edgesToRefine.containsKey(edge)) {
				curBoundary.add(edgesToRefine.get(edge));
				hasRefined = true;
			}
			curBoundary.add(edge.end);
		}
		boundary = new Boundary(curBoundary, boundary.isHole);
		if(hasRefined)
			return refineBoundary(boundary, edgesToRefine);
		return boundary;
	}

	private List<OrientedEdge> polygonPointsToEdge(List<PathObject> points){
		var edges = new ArrayList<OrientedEdge>();
		for (var i = 0; i < points.size(); i++) {
			var j = (i + 1) % points.size();
			var pI = points.get(i);
			var pJ = points.get(j);
			var edge = new OrientedEdge(pI, pJ);
			edges.add(edge);
		}
		return edges;
	}

	private void computeCellDistanceToBoundaries(Collection<PathObject> ductCells, Collection<Boundary> boundaries, Map<PathObject, List<PathObject>> ductNeighbors) {
		Map<PathObject, Integer> distanceToBoundaries = new HashMap<>();
		Map<PathObject, Integer> numberConnectedBoundaries = new HashMap<>(); // None (-1), Hole (0), Perimeter (1), Both (2)
		Queue<PathObject> cellQueue = new ArrayDeque<>();
		for (PathObject cell : ductCells) {
			if(cell.getMeasurementList().containsNamedMeasurement(DISTANCE_TO_BOUNDARIES)){
				distanceToBoundaries.put(cell, 0);
				cellQueue.add(cell);
			}else
				distanceToBoundaries.put(cell, -1);
			numberConnectedBoundaries.put(cell, 0);
		}
		boundaries.stream().forEach(b ->{
			b.cells.forEach(c->{
				var nbConnectedBoundaries = numberConnectedBoundaries.get(c);
				numberConnectedBoundaries.put(c, nbConnectedBoundaries + 1);
			});
		});

		while(!cellQueue.isEmpty()) {
			var curCell = cellQueue.poll();
			var neighbors = ductNeighbors.get(curCell);
			var newNeighborDist = distanceToBoundaries.get(curCell) + 1;
			for (PathObject neighbor : neighbors) {
				var curDist = distanceToBoundaries.get(neighbor);
				if(curDist == -1 || curDist > newNeighborDist){
					distanceToBoundaries.put(neighbor, newNeighborDist);
					cellQueue.add(neighbor);
				}
			}
		}

		for (PathObject cell : ductCells) {
			var dist = distanceToBoundaries.get(cell);
			cell.getMeasurementList().putMeasurement(DISTANCE_TO_BOUNDARIES, dist);
			var nbConnectedBoundaries = numberConnectedBoundaries.get(cell);
			cell.getMeasurementList().putMeasurement(IS_IN_MONOLAYER, nbConnectedBoundaries >= 2 ? 1 : 0);
		}
	}

	private static Point2 getNucleusCentroid(PathObject cell) {
		var nucleus = PathObjectTools.getROI(cell, true);
		return new Point2(nucleus.getCentroidX(), nucleus.getCentroidY());
	}

}
