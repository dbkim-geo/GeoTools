package org.geotools.tutorial.crs;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.JProgressWindow;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.ProgressListener;

public class CRSLabAnotherButton {

	private File sourceFile;
	private SimpleFeatureSource featureSource;
	private MapContent map;
	
	public static void main(String[] args) throws Exception{
		CRSLabAnotherButton lab = new CRSLabAnotherButton();
		lab.displayShapefile();
	}

	private void displayShapefile() throws Exception{
		sourceFile = JFileDataStoreChooser.showOpenFile("shp", null);
		if (sourceFile == null) {
			return;
		}
		
		Map<String, Object> params = new HashMap<>();
		params.put("url", sourceFile.toURI().toURL());
		params.put("create spatial index", false);
		params.put("memory mapped buffer", false);
		params.put("charset", "UTF-8");
		
		DataStore store = DataStoreFinder.getDataStore(params);
		featureSource = store.getFeatureSource(store.getTypeNames()[0]);
//		FileDataStore store = FileDataStoreFinder.getDataStore(sourceFile);
//		featureSource = store.getFeatureSource();
		
		
		// Map 콘텐츠 형성, shp 추가
		map = new MapContent();
		Style style = SLD.createSimpleStyle(featureSource.getSchema());
		Layer layer = new FeatureLayer(featureSource, style);
		map.setTitle("CRS GUI");
		map.addLayer(layer);

		JMapFrame mapFrame = new JMapFrame(map);
		mapFrame.enableToolBar(true);
		mapFrame.enableStatusBar(true);
		
		JToolBar toolbar = mapFrame.getToolBar();
		toolbar.addSeparator();
		toolbar.add(new JButton(new ValidateGeometryAction())); //Geometry 유효성 검사 기능 버튼
		toolbar.add(new JButton(new ExportShapefileAction())); //Reproject된 feature를 export하는 기능 버튼

		mapFrame.setSize(800, 600);
		mapFrame.setVisible(true);
	}
	
	/*
	 * Geometry 유효성 검사 기능 메소드
	 * */
	class ValidateGeometryAction extends SafeAction{
		ValidateGeometryAction() {
			super("Validate geometry");
			putValue(Action.SHORT_DESCRIPTION, "Check each geometry");
		}
		
		public void action(ActionEvent e) throws Throwable {
            SwingWorker worker = new SwingWorker<String, Object>() {
                protected String doInBackground() throws Exception {
                    final JProgressWindow progress = new JProgressWindow(null);
                    progress.setTitle("Validating feature geometry");

                    int numInvalid = validateFeatureGeometry(progress);
                    if (numInvalid == 0) {
                        return "All feature geometries are valid";
                    } else {
                        return "Invalid geometries: " + numInvalid;
                    }
                }

                protected void done() {
                    try {
                        Object result = get();
                        JOptionPane.showMessageDialog(null, result, "Geometry results", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ignore) {
                    }
                }
            };
            worker.execute();
        }
		
		private int validateFeatureGeometry(ProgressListener progress) throws Exception {
			final SimpleFeatureCollection featureCollection = featureSource.getFeatures();
			
			class ValidationVisitor implements FeatureVisitor{
				public int numInvalidGeometries = 0;
				
				public void visit(Feature f) {
					SimpleFeature feature = (SimpleFeature) f;
					Geometry geom = (Geometry) feature.getDefaultGeometry();
					if (geom != null && !geom.isValid()) {
						numInvalidGeometries++;
						System.out.println("Invalid Geometry: "+ feature.getID());
					}
				}
			}
			
			ValidationVisitor visitor = new ValidationVisitor();
			featureCollection.accepts(visitor, progress);
			
			return visitor.numInvalidGeometries;
		}

	}
	
	/*
	 * Reproject한 feature를 export하는 기능 메소드
	 * */
	class ExportShapefileAction extends SafeAction{
		ExportShapefileAction() {
			super("Export...");
			putValue(Action.SHORT_DESCRIPTION, "Export using current crs");
		}
		
		public void action(ActionEvent e) throws Throwable {
			exportToShapefile();
		}

		//reproject data를 shp로 export함
		private void exportToShapefile() throws Exception {
			SimpleFeatureType schema = featureSource.getSchema();
			JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
			chooser.setDialogTitle("Save reprojected shapefile");
			chooser.setSaveFile(sourceFile);
			int returnVal = chooser.showSaveDialog(null);
			if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
				return;
			}
			File file = chooser.getSelectedFile();
			if (file.equals(sourceFile)) {
				JOptionPane.showMessageDialog(null, "Cannot replace "+file);
				return;
			}
			
			//math transform 세팅
			CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
			CoordinateReferenceSystem worldCRS = map.getCoordinateReferenceSystem();
			boolean lenient = true;
			MathTransform transform = CRS.findMathTransform(dataCRS, worldCRS, lenient);
			
			//feature 갖고오기
			SimpleFeatureCollection featureCollection = featureSource.getFeatures();
			
			//새로운 shp를 만들려고 FeatureType하나를 새로 만듦
			//why? old shp와 new shp는 crs만 다르기 때문에!
			DataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			Map<String, Serializable> create = new HashMap<>();
			create.put("url", file.toURI().toURL());
			create.put("create spatial index", Boolean.TRUE);
			DataStore dataStore = factory.createNewDataStore(create);
			SimpleFeatureType featureType = SimpleFeatureTypeBuilder.retype(schema, worldCRS);
			dataStore.createSchema(featureType);
			//여기서 만드는 new shp 이름은 아래에서 FeatureWriter로 쓰임
			String createdName = dataStore.getTypeNames()[0];
			
			//Transaction
			Transaction transaction = new DefaultTransaction("Reproject");
			try (
					FeatureWriter<SimpleFeatureType, SimpleFeature> writer = dataStore.getFeatureWriterAppend(createdName, transaction);
					SimpleFeatureIterator iterator = featureCollection.features()) {
			        while (iterator.hasNext()) {
			            SimpleFeature feature = iterator.next();
			            SimpleFeature copy = writer.next();
			            copy.setAttributes(feature.getAttributes());
			
			            Geometry geometry = (Geometry) feature.getDefaultGeometry();
			            Geometry geometry2 = JTS.transform(geometry, transform);
			
			            copy.setDefaultGeometry(geometry2);
			            writer.write();
			        }
			        transaction.commit();
			        JOptionPane.showMessageDialog(null, "Export to shapefile complete");
			    } catch (Exception problem) {
			        problem.printStackTrace();
			        transaction.rollback();
			        JOptionPane.showMessageDialog(null, "Export to shapefile failed");
			    } finally {
			        transaction.close();
			    }
			
		}
		
		
	}
	
}
