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

public class CRSFunction {

	private File sourceFile;
	private SimpleFeatureSource featureSource;
	private MapContent map;
	
	public static void main(String[] args) throws Exception{
		CRSFunction lab = new CRSFunction();
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
		
		
		// Map ????????? ??????, shp ??????
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
		toolbar.add(new JButton(new ValidateGeometryAction())); //Geometry ????????? ?????? ?????? ??????
		toolbar.add(new JButton(new ExportShapefileAction())); //Reproject??? feature??? export?????? ?????? ??????
		toolbar.add(new JButton(new ShowCRSasWKTAction()));
		mapFrame.setSize(800, 600);
		mapFrame.setVisible(true);
	}
	
	/*
	 * Geometry ????????? ?????? ?????? ?????????
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
	 * Reproject??? feature??? export?????? ?????? ?????????
	 * */
	class ExportShapefileAction extends SafeAction{
		ExportShapefileAction() {
			super("Export...");
			putValue(Action.SHORT_DESCRIPTION, "Export using current crs");
		}
		
		public void action(ActionEvent e) throws Throwable {
			exportToShapefile();
		}

		//reproject data??? shp??? export???
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
			
			//math transform ??????
			CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
			CoordinateReferenceSystem worldCRS = map.getCoordinateReferenceSystem();
			boolean lenient = true;
			MathTransform transform = CRS.findMathTransform(dataCRS, worldCRS, lenient);
			
			//feature ????????????
			SimpleFeatureCollection featureCollection = featureSource.getFeatures();
			
			//????????? shp??? ???????????? FeatureType????????? ?????? ??????
			//why? old shp??? new shp??? crs??? ????????? ?????????!
			DataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			Map<String, Serializable> create = new HashMap<>();
			create.put("url", file.toURI().toURL());
			create.put("create spatial index", Boolean.TRUE);
			DataStore dataStore = factory.createNewDataStore(create);
			SimpleFeatureType featureType = SimpleFeatureTypeBuilder.retype(schema, worldCRS);
			dataStore.createSchema(featureType);
			//????????? ????????? new shp ????????? ???????????? FeatureWriter??? ??????
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
	
	/*
	 * CRS??? WKT ???????????? ???????????? ??????
	 * */
	class ShowCRSasWKTAction extends SafeAction {
		ShowCRSasWKTAction(){
			super("Coordinate Reference System");
			putValue(Action.SHORT_DESCRIPTION, "Show Coordinate Reference System as WKT");
		}
		public void action(ActionEvent e) throws Throwable {
			showCRSasWKT();
		}
		private void showCRSasWKT() throws Exception {
			// TODO Auto-generated method stub
			SimpleFeatureType schema = featureSource.getSchema();
			CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
			JOptionPane.showMessageDialog(null, dataCRS, "Coordinate Reference System", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
	}
	
}
