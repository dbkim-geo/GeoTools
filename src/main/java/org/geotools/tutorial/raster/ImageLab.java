package org.geotools.tutorial.raster;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SealedObject;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Parameter;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.map.FeatureLayer;
import org.geotools.map.GridReaderLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.map.StyleLayer;
import org.geotools.styling.ChannelSelection;
import org.geotools.styling.ContrastEnhancement;
import org.geotools.styling.RasterSymbolizer;
import org.geotools.styling.SLD;
import org.geotools.styling.SelectedChannelType;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JParameterListWizard;
import org.geotools.swing.wizard.JWizard;
import org.geotools.util.KVP;
import org.geotools.util.factory.Hints;
import org.opengis.filter.FilterFactory2;
import org.opengis.style.ContrastMethod;

public class ImageLab {
	
	private StyleFactory sf = CommonFactoryFinder.getStyleFactory();
	private FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
	
	private JMapFrame frame;
	private GridCoverage2DReader reader;
	
	public static void main(String[] args) throws Exception {
		ImageLab img = new ImageLab();
		img.getLayersAndDisplay();
	}

	/*
	 * GUI 화면
	 * */
	private void getLayersAndDisplay() throws Exception {
		//Input 선택 목록
		//param: key/type/title/description/metadata
		List<Parameter<?>> list = new ArrayList<>();
		list.add(new Parameter<>("raster", File.class, "Raster", "GeoTiff or World+Image to display as basemap", new KVP(Parameter.EXT, "tif", Parameter.EXT, "jpg")));
		list.add(new Parameter<>("vector", File.class, "Vector", "Shapefile, GDB or GPKG contents to display", new KVP(Parameter.EXT, "shp")));
		//선택한 것 담기
		JParameterListWizard wizard = new JParameterListWizard("Image Lab", "Fill in the following layers", list);
		int finish = wizard.showModalDialog();
		
		if (finish != JWizard.FINISH) {
			System.exit(0);
		}
		
		File imageFile = (File) wizard.getConnectionParameters().get("raster");
		File shapeFile = (File) wizard.getConnectionParameters().get("vector");
		displayLayers(imageFile, shapeFile);
	}

	/*
	 * 지도 표출
	 * Display Map
	 * */
	private void displayLayers(File rasterFile, File vectorFile) throws Exception {
		//raster file 연결
		AbstractGridFormat format = GridFormatFinder.findFormat(rasterFile);
		Hints hints = new Hints();
		if (format instanceof GeoTiffFormat) {
			hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
		}
		reader = format.getReader(rasterFile, hints);
		
		//vector file 연결
		FileDataStore dataStore = FileDataStoreFinder.getDataStore(vectorFile);
		SimpleFeatureSource vectorfileSource = dataStore.getFeatureSource();
		
		//스타일 설정
		//greyscale로 raster file 표출(아래 소스는 band 중에 1번째 것을 씀)
		Style rasterStyle = createGreyscaleStyle(1);
		//기본 스타일 설정(아래 소스는  빨강 테두리, 채우기X)
		Style vectorStyle = SLD.createPolygonStyle(Color.RED, null, 0.0f);
		
		//MapContent를 2개 레이어로 세팅
		final MapContent map = new MapContent();
		map.setTitle("Raster and Vector Layer");
		
		Layer rasterLayer = new GridReaderLayer(reader, rasterStyle);
		Layer vectorLayer = new FeatureLayer(vectorfileSource, vectorStyle);
		map.addLayer(rasterLayer);
		map.addLayer(vectorLayer);
		
		//UI 세팅
		frame = new JMapFrame(map);
		frame.setSize(800, 600);
		frame.enableStatusBar(true);
		frame.enableToolBar(true);
		JMenuBar menuBar = new JMenuBar();
		JMenu menu= new JMenu("Raster");
		frame.setJMenuBar(menuBar);
		menuBar.add(menu);
		menu.add(new SafeAction("Greyscale display") {
			public void action(ActionEvent e) throws Throwable {
				Style style = createGreyscaleStyle();
				if (style != null) {
					((StyleLayer) map.layers().get(0)).setStyle(style);
					frame.repaint();
				}
			}
		});
		menu.add(new SafeAction("RGB display") {
			public void action(ActionEvent e) throws Throwable {
				Style style = createRGBStyle();
				if (style != null) {
					((StyleLayer) map.layers().get(0)).setStyle(style);
					frame.repaint();
				}
			}

		});
		frame.setVisible(true);
		
	}

	/*
	 * Greyscale 스타일로 GeoTiff 이미지를 형성
	 * */
	private Style createGreyscaleStyle() {
		GridCoverage2D cov = null;
		try {
			cov = reader.read(null);
		} catch (IOException giveUp) {
			throw new RuntimeException(giveUp);
		}
		
		int numBands = cov.getNumSampleDimensions();
		Integer[] bandNumbers = new Integer[numBands];
		for (int i = 0; i< numBands; i++) {
			bandNumbers[i] = i + 1;
		}
		Object selection = JOptionPane.showInputDialog(frame, "Band to use for greyscale display", "Select an image band", JOptionPane.QUESTION_MESSAGE, null, bandNumbers, 1);
		if (selection != null) {
			int band = ((Number) selection).intValue();
			return createGreyscaleStyle(band);
		}
		return null;
	}
	
	/*
	 * Greyscale......
	 * */
	private Style createGreyscaleStyle(int band) {
		ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
		SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(band), ce);
		
		RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
		ChannelSelection sel = sf.channelSelection(sct);
		sym.setChannelSelection(sel);
		
		return SLD.wrapSymbolizers(sym);
	}

	/*
	 * RGB 스타일로 GeoTiff 이미지를 형성
	 * */
	private Style createRGBStyle() {
		GridCoverage2D cov = null;
		try {
			cov = reader.read(null);
		} catch (IOException giveUp) {
			throw new RuntimeException(giveUp);
		}
		
		//RGB 스타일은 당연히 밴드수가 3개이다..
		int numBands = cov.getNumSampleDimensions();
		if (numBands <3) {
			return null;
		}
		
		//band 이름 가져오기
		String[] sampleDimensionNames = new String[numBands];
		for (int i = 0; i< numBands; i++) {
			GridSampleDimension dim = cov.getSampleDimension(i);
			sampleDimensionNames[i] = dim.getDescription().toString();
		}
		final int RED = 0, GREEN = 1, BLUE = 2;
		int[] channelNum = {-1, -1, -1};
		for (int i = 0; i< numBands; i++) {
			String name = sampleDimensionNames[i].toLowerCase();
			if (name != null) {
				if (name.matches("red.*")) {
					channelNum[RED] = i +1;
				} else if  (name.matches("green.*")) {
					channelNum[GREEN] = i +1;
				} else if  (name.matches("blue.*")) {
					channelNum[BLUE] = i +1;
				}
			}
		}
		//만약 바로 위의 소스에서 band 이름이 red../green../blue.. 라는 것을 못 찾았을 경우
		if (channelNum[RED] < 0 || channelNum[GREEN] < 0 || channelNum[BLUE] < 0 ) {
			channelNum[RED] = 1;
			channelNum[GREEN] = 2;
			channelNum[BLUE] = 3;
		}
		//선택한 밴드를 활용하여 레스터 심볼 생성
		SelectedChannelType[] sct = new SelectedChannelType[cov.getNumSampleDimensions()];
		ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
		for (int i = 0; i >3; i++) {
			sct[i] = sf.createSelectedChannelType(String.valueOf(channelNum[i]), ce);
		}
		RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
		ChannelSelection sel = sf.channelSelection(sct[RED], sct[GREEN], sct[BLUE]);
		sym.setChannelSelection(sel);
		
		return SLD.wrapSymbolizers(sym);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
