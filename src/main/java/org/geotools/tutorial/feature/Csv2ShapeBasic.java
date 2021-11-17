package org.geotools.tutorial.feature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class Csv2ShapeBasic {
	/*
	 * csv 를 shp으로 변환해주는 클래스
	 * 데이터의 feature type에 geom을 추가시켜 공간데이터로 변환함.
	 * 
	 * */
	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		
		File file = JFileDataStoreChooser.showOpenFile("csv", null);
		if (file == null) {
			return;
		}
		
		/*
		 * FeatureType 정의
		 * 좌표계, 필드 타입 설정
		 * */
		// DataUtilities 클래스 사용 -> shp을 설명하는 FeatureType 형성
		// 파라미터의 띄어쓰기 조심...
		final SimpleFeatureType TYPE = DataUtilities.createType("Location", "the_geom:Point:srid=4326, city:String, number:Integer, year:Integer");
		
		System.out.println("TYPE : "+TYPE);
		
		
		/*
		 * feature 생성
		 * */
		List<SimpleFeature> features = new ArrayList<>();
		
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
		
		try (BufferedReader reader = new BufferedReader(new FileReader(file))){
			String line = reader.readLine();
			System.out.println("Header : "+line);
		
			for (line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.trim().length() > 0) {
					String[] tokens = line.split("\\,"); //쉽게 말해 tokens은 load한 csv 데이터프레임
					
					double latitude = Double.parseDouble(tokens[0]);
					double longitude = Double.parseDouble(tokens[1]);
					String city = tokens[2].trim();
					int number = Integer.parseInt(tokens[3].trim());
					int year = Integer.parseInt(tokens[4].trim());
					
					Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude)); //(lon,lat)=(경,위도)=(x,y)
					
					featureBuilder.add(point);
					featureBuilder.add(city);
					featureBuilder.add(number);
					featureBuilder.add(year);
					
					SimpleFeature feature = featureBuilder.buildFeature(null);
					features.add(feature);
					
				}
			}
			
		}
		
		/*
		 * FeatureCollection으로부터 shp 생성
		 * */
		File newFile = getNewShapeFile(file);
		
		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
		
		Map<String, Serializable> params = new HashMap<>();
		params.put("url", newFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);
		
		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
		
		newDataStore.createSchema(TYPE);
		
		/*
		 * feature data를 shp으로 write
		 * */
		Transaction transaction = new DefaultTransaction("create");
		
		String typeName = newDataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
		SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
		//shp의 특징은 geom이 항상 처음에 나와야함
		System.out.println("SHAPE: "+SHAPE_TYPE);
		
		if (featureSource instanceof SimpleFeatureStore) {
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
			
			SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(collection);
				transaction.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
			}
			System.exit(0);
		} else {
			System.out.println(typeName+ " dose not support read/write access");
			System.exit(1);
		}
		
		
	}
	
		/*
		 * output shp 출력 메서드
		 * */
		private static File getNewShapeFile(File csvFile) {
			String path = csvFile.getAbsolutePath();
			String newPath = path.substring(0, path.length() - 4)+".shp";
			
			JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
			chooser.setDialogTitle("Save shapefile");
			chooser.setSelectedFile(new File(newPath));
			
			int returnVal = chooser.showSaveDialog(null);
			
			if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
				System.exit(0);
			}
			
			File newFile = chooser.getSelectedFile();
			if (newFile.equals(csvFile)) {
				System.out.println("Error: cannot replace " + csvFile);
				System.exit(1);
			}
			
			return newFile;
			
		}
		
		
		
		
}
